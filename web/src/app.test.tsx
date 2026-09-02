import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { App } from "./app";

const session = {
  authenticated: true,
  playerId: "player-1",
  csrfToken: "csrf-token",
};

const project = {
  id: "project-1",
  name: "Castle",
  ownerName: "Alex",
  lastModifiedAt: 2,
};

const detail = {
  id: "project-1",
  name: "Castle",
  fileName: "castle.litematic",
  hash: "abc",
  owner: { id: "player-1", name: "Alex" },
  lastModifiedBy: { id: "player-2", name: "Sam" },
  createdAt: 1,
  lastModifiedAt: 2,
  position: { dimension: "minecraft:overworld", x: 10, y: 64, z: 20 },
  rotation: "NONE",
  mirror: "NONE",
  materialAvailability: "AVAILABLE",
};

function json(body: unknown, status = 200) {
  return Promise.resolve(
    new Response(status === 204 ? null : JSON.stringify(body), {
      status,
      headers: { "Content-Type": "application/json" },
    }),
  );
}

function routeFetch(
  routes: Record<string, unknown | ((request: RequestInfo | URL, init?: RequestInit) => unknown)>,
) {
  return vi.fn<typeof fetch>((request, init) => {
    const url = String(request);
    const route = routes[`${init?.method ?? "GET"} ${url}`] ?? routes[url];
    if (typeof route === "function") {
      return json(route(request, init));
    }
    if (route instanceof Response) {
      return Promise.resolve(route);
    }
    if (route === undefined) {
      throw new Error(`Unexpected request: ${init?.method ?? "GET"} ${url}`);
    }
    return json(route);
  });
}

function renderApp(path = "/") {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>,
  );
}

describe("App authentication", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", vi.fn());
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it("shows a skeleton while checking the session", () => {
    vi.mocked(fetch).mockReturnValue(new Promise(() => {}));
    renderApp();

    expect(screen.getByLabelText("Loading session")).toBeInTheDocument();
  });

  it("logs in with the player name and password without storing a token", async () => {
    const user = userEvent.setup();
    const fetcher = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({ code: "unauthorized", message: "Authentication required" }),
          { status: 401, headers: { "Content-Type": "application/json" } },
        ),
      )
      .mockResolvedValueOnce(
        new Response(JSON.stringify(session), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        }),
      )
      .mockResolvedValueOnce(
        new Response(JSON.stringify([]), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        }),
      );
    vi.stubGlobal("fetch", fetcher);
    const storage = vi.spyOn(Storage.prototype, "setItem");
    renderApp();

    await user.type(await screen.findByLabelText("Player name"), "Alex");
    await user.type(screen.getByLabelText("Password"), "secret");
    await user.click(screen.getByRole("button", { name: "Sign in" }));

    await screen.findByText("No projects yet");
    expect(fetcher).toHaveBeenNthCalledWith(
      2,
      "/api/v1/auth/login",
      expect.objectContaining({
        body: JSON.stringify({ name: "Alex", password: "secret" }),
        credentials: "same-origin",
        method: "POST",
      }),
    );
    expect(storage).not.toHaveBeenCalled();
  });
});

describe("App project views", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("renders empty and stable error states", async () => {
    const user = userEvent.setup();
    vi.stubGlobal(
      "fetch",
      vi
        .fn<typeof fetch>()
        .mockResolvedValueOnce(await json(session))
        .mockResolvedValueOnce(
          await json({ code: "server_timeout", message: "Timed out" }, 503),
        )
        .mockResolvedValueOnce(await json([])),
    );
    renderApp();

    expect(await screen.findByText("The server did not respond in time.")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Try again" }));
    expect(await screen.findByText("No projects yet")).toBeInTheDocument();
  });

  it("filters projects and sorts them by name", async () => {
    const user = userEvent.setup();
    vi.stubGlobal(
      "fetch",
      routeFetch({
        "/api/v1/auth/session": session,
        "/api/v1/projects": [
          project,
          { ...project, id: "project-2", name: "Aquarium", lastModifiedAt: 3 },
        ],
      }),
    );
    renderApp();

    await screen.findByText("Castle");
    await user.selectOptions(screen.getByLabelText("Sort projects"), "name");
    const links = screen.getAllByTestId("project-link");
    expect(
      links.map((link) => within(link).getByRole("heading").textContent),
    ).toEqual(["Aquarium", "Castle"]);

    await user.type(screen.getByLabelText("Filter projects"), "cast");
    expect(screen.getByText("Castle")).toBeInTheDocument();
    expect(screen.queryByText("Aquarium")).not.toBeInTheDocument();
  });

  it("shows localized material names instead of raw item IDs", async () => {
    const user = userEvent.setup();
    vi.stubGlobal(
      "fetch",
      routeFetch({
        "/api/v1/auth/session": session,
        "/api/v1/materials/summary": [
          {
            itemId: "minecraft:stone",
            translationKey: "block.minecraft.stone",
            fallbackName: "Stone",
            variant: "",
            required: 10,
            supplied: 2,
            missing: 8,
            progressPercent: 20,
          },
        ],
      }),
    );
    renderApp("/materials");

    expect(await screen.findByText("Stone")).toBeInTheDocument();
    expect(screen.queryByText("minecraft:stone")).not.toBeInTheDocument();
    expect(screen.getByRole("columnheader", { name: "Missing" })).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "Most missing" })).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "切换到中文" }));
    expect(screen.getByText("石头")).toBeInTheDocument();
    expect(screen.queryByText("Stone")).not.toBeInTheDocument();
    expect(screen.getByRole("columnheader", { name: "缺少" })).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "缺少最多" })).toBeInTheDocument();
  });

  it("shows claim conflicts without silently changing material ownership", async () => {
    const user = userEvent.setup();
    vi.stubGlobal(
      "fetch",
      routeFetch({
        "/api/v1/auth/session": session,
        "/api/v1/projects/project-1": detail,
        "/api/v1/projects/project-1/materials": [
          {
            itemId: "minecraft:stone",
            translationKey: "block.minecraft.stone",
            fallbackName: "Stone",
            variant: "",
            required: 10,
            supplied: 2,
            missing: 8,
            progressPercent: 20,
            claimants: [],
          },
        ],
        "PUT /api/v1/projects/project-1/materials/minecraft%3Astone/claim?variant=":
          new Response(
            JSON.stringify({ code: "claim_conflict", message: "Already claimed" }),
            { status: 409, headers: { "Content-Type": "application/json" } },
          ),
      }),
    );
    renderApp("/projects/project-1");

    await user.click(await screen.findByRole("button", { name: "Claim material" }));

    expect(
      await screen.findByText("This item is claimed by another player."),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Claim material" })).toBeInTheDocument();
  });

  it("releases all of the current player's material claims", async () => {
    const user = userEvent.setup();
    const fetcher = routeFetch({
      "/api/v1/auth/session": session,
      "/api/v1/projects/project-1": detail,
      "/api/v1/projects/project-1/materials": [
        {
          itemId: "minecraft:stone",
          translationKey: "block.minecraft.stone",
          fallbackName: "Stone",
          variant: "",
          required: 10,
          supplied: 2,
          missing: 8,
          progressPercent: 20,
          claimants: [{ id: session.playerId, name: "Alex" }],
        },
      ],
      "DELETE /api/v1/projects/project-1/material-claims/me": {
        outcome: "released",
      },
    });
    vi.stubGlobal("fetch", fetcher);
    renderApp("/projects/project-1");

    await user.click(await screen.findByRole("button", { name: "Unclaim all materials" }));

    await waitFor(() =>
      expect(fetcher).toHaveBeenCalledWith(
        "/api/v1/projects/project-1/material-claims/me",
        expect.objectContaining({ method: "DELETE" }),
      ),
    );
  });

  it("shows the stocking coordinate form only to the project owner", async () => {
    const user = userEvent.setup();
    vi.stubGlobal(
      "fetch",
      routeFetch({
        "/api/v1/auth/session": session,
        "/api/v1/projects/project-1": detail,
        "/api/v1/projects/project-1/materials": [],
        "/api/v1/projects/project-1/stocking-area": {
          dimension: "minecraft:overworld",
          minX: 1,
          minY: 2,
          minZ: 3,
          maxX: 4,
          maxY: 5,
          maxZ: 6,
          volume: 120,
        },
      }),
    );
    renderApp("/projects/project-1");

    await user.click(await screen.findByRole("tab", { name: "Stocking Area" }));
    expect(await screen.findByLabelText("Dimension · Overworld")).toHaveValue("minecraft:overworld");
    expect(screen.getByRole("button", { name: "Save stocking area" })).toBeInTheDocument();
  });

  it("localizes dimension names without changing editable dimension IDs", async () => {
    const user = userEvent.setup();
    vi.stubGlobal(
      "fetch",
      routeFetch({
        "/api/v1/auth/session": session,
        "/api/v1/projects/project-1": detail,
        "/api/v1/projects/project-1/materials": [],
        "/api/v1/projects/project-1/stocking-area": {
          dimension: "minecraft:overworld",
          minX: 1,
          minY: 2,
          minZ: 3,
          maxX: 4,
          maxY: 5,
          maxZ: 6,
          volume: 120,
        },
      }),
    );
    renderApp("/projects/project-1");

    expect(await screen.findByText("Overworld · 10, 64, 20")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "切换到中文" }));
    expect(screen.getByText("主世界 · 10, 64, 20")).toBeInTheDocument();

    await user.click(screen.getByRole("tab", { name: "库存区" }));
    expect(await screen.findByLabelText("维度 · 主世界")).toHaveValue("minecraft:overworld");
  });

  it("keeps stocking controls read-only for non-owners", async () => {
    const user = userEvent.setup();
    vi.stubGlobal(
      "fetch",
      routeFetch({
        "/api/v1/auth/session": session,
        "/api/v1/projects/project-1": {
          ...detail,
          owner: { id: "someone-else", name: "Taylor" },
        },
        "/api/v1/projects/project-1/materials": [],
        "/api/v1/projects/project-1/stocking-area": new Response(
          JSON.stringify({ code: "stocking_area_not_found", message: "Not found" }),
          { status: 404, headers: { "Content-Type": "application/json" } },
        ),
      }),
    );
    renderApp("/projects/project-1");

    await user.click(await screen.findByRole("tab", { name: "Stocking Area" }));
    expect(await screen.findByText("Only the project owner can edit this area.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Save stocking area" })).not.toBeInTheDocument();
  });

  it("claims a build region with its encoded name", async () => {
    const user = userEvent.setup();
    const fetcher = routeFetch({
      "/api/v1/auth/session": session,
      "/api/v1/projects/project-1": detail,
      "/api/v1/projects/project-1/materials": [],
      "/api/v1/projects/project-1/build-regions": [
        {
          name: "North Hall",
          requiredBlocks: 100,
          placedBlocks: 40,
          scanned: true,
          lastScanAt: 2,
          progressPercent: 40,
          claimants: [],
        },
      ],
      "PUT /api/v1/projects/project-1/build-regions/North%20Hall/claim": {
        outcome: "claimed",
      },
    });
    vi.stubGlobal("fetch", fetcher);
    renderApp("/projects/project-1");

    await user.click(await screen.findByRole("tab", { name: "Build Regions" }));
    await user.click(await screen.findByRole("button", { name: "Claim region" }));

    await waitFor(() =>
      expect(fetcher).toHaveBeenCalledWith(
        "/api/v1/projects/project-1/build-regions/North%20Hall/claim",
        expect.objectContaining({ method: "PUT" }),
      ),
    );
  });
});

describe("App preferences and polling", () => {
  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it("switches language and theme", async () => {
    const user = userEvent.setup();
    vi.stubGlobal(
      "fetch",
      routeFetch({
        "/api/v1/auth/session": session,
        "/api/v1/projects": [],
      }),
    );
    const { container } = renderApp();
    await screen.findByText("No projects yet");

    await user.click(screen.getByRole("button", { name: "切换到中文" }));
    expect(screen.getByText("暂无项目")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "切换到深色主题" }));
    expect(container.querySelector(".app-shell")).toHaveAttribute("data-theme", "dark");
  });

  it("polls every five seconds only while the page is visible", async () => {
    vi.useFakeTimers();
    const fetcher = routeFetch({
      "/api/v1/auth/session": session,
      "/api/v1/projects": [],
    });
    vi.stubGlobal("fetch", fetcher);
    renderApp();
    await act(async () => Promise.resolve());
    expect(fetcher).toHaveBeenCalledTimes(2);

    await act(async () => vi.advanceTimersByTimeAsync(5_000));
    expect(fetcher).toHaveBeenCalledTimes(3);

    Object.defineProperty(document, "visibilityState", {
      configurable: true,
      value: "hidden",
    });
    document.dispatchEvent(new Event("visibilitychange"));
    await act(async () => vi.advanceTimersByTimeAsync(10_000));
    expect(fetcher).toHaveBeenCalledTimes(3);

    Object.defineProperty(document, "visibilityState", {
      configurable: true,
      value: "visible",
    });
  });
});

import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  ApiError,
  createApiClient,
  errorMessage,
  type Session,
} from "./api";

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

describe("API client", () => {
  const session: Session = {
    authenticated: true,
    playerId: "player-1",
    csrfToken: "csrf-value",
  };

  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it("uses same-origin cookies and adds CSRF only to authenticated writes", async () => {
    const fetcher = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(json(session))
      .mockResolvedValueOnce(json({ outcome: "claimed" }));
    const api = createApiClient(fetcher);

    await api.session();
    await api.setMaterialClaim("project/one", "minecraft:stone", "smooth", true);

    expect(fetcher).toHaveBeenNthCalledWith(
      1,
      "/api/v1/auth/session",
      expect.objectContaining({ credentials: "same-origin", method: "GET" }),
    );
    const [url, options] = fetcher.mock.calls[1];
    expect(url).toBe(
      "/api/v1/projects/project%2Fone/materials/minecraft%3Astone/claim?variant=smooth",
    );
    expect(options?.method).toBe("PUT");
    expect(new Headers(options?.headers).get("X-CSRF-Token")).toBe("csrf-value");
  });

  it("aggregates the signed-in player's claims in one endpoint", async () => {
    const fetcher = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(json(session))
      .mockResolvedValueOnce(json({ materials: [], regions: [] }));
    const api = createApiClient(fetcher);

    await api.session();
    await api.myClaims();

    expect(fetcher).toHaveBeenNthCalledWith(
      2,
      "/api/v1/claims/me",
      expect.objectContaining({ credentials: "same-origin", method: "GET" }),
    );
  });

  it("does not retry a failed write and reports unauthorized responses", async () => {
    const unauthorized = vi.fn();
    const fetcher = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(json(session))
      .mockResolvedValueOnce(
        json({ code: "unauthorized", message: "Authentication required" }, 401),
      );
    const api = createApiClient(fetcher, unauthorized);

    await api.session();
    await expect(api.setBuildClaim("project-1", "north", true)).rejects.toEqual(
      expect.objectContaining({ code: "unauthorized", status: 401 }),
    );

    expect(fetcher).toHaveBeenCalledTimes(2);
    expect(unauthorized).toHaveBeenCalledOnce();
  });

  it("manages the stocking area registry and binds projects by area id", async () => {
    const fetcher = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(json(session))
      .mockResolvedValueOnce(json([]))
      .mockResolvedValueOnce(json({ outcome: "created" }))
      .mockResolvedValueOnce(json({ outcome: "deleted" }))
      .mockResolvedValueOnce(json({ outcome: "updated" }));
    const api = createApiClient(fetcher);
    const definition = {
      dimension: "minecraft:overworld",
      minX: 0,
      minY: 0,
      minZ: 0,
      maxX: 1,
      maxY: 1,
      maxZ: 1,
    };

    await api.session();
    await api.stockingAreas();
    await api.createStockingArea({ name: "yard", ...definition });
    await api.deleteStockingArea("area-1", true);
    await api.setStockingAreaRef("project-1", null);

    expect(fetcher).toHaveBeenNthCalledWith(
      2,
      "/api/v1/stocking-areas",
      expect.objectContaining({ credentials: "same-origin", method: "GET" }),
    );
    expect(fetcher).toHaveBeenNthCalledWith(
      3,
      "/api/v1/stocking-areas",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ name: "yard", ...definition }),
      }),
    );
    expect(fetcher).toHaveBeenNthCalledWith(
      4,
      "/api/v1/stocking-areas/area-1?force=true",
      expect.objectContaining({ method: "DELETE" }),
    );
    expect(fetcher).toHaveBeenNthCalledWith(
      5,
      "/api/v1/projects/project-1/stocking-area",
      expect.objectContaining({
        method: "PUT",
        body: JSON.stringify({ areaId: null }),
      }),
    );
    expect(new Headers(fetcher.mock.calls[4][1]?.headers).get("X-CSRF-Token")).toBe(
      "csrf-value",
    );
  });

  it("translates stable server error codes in both languages", () => {
    const error = new ApiError(409, "claim_conflict", "Already claimed");

    expect(errorMessage(error, "en")).toBe("This item is claimed by another player.");
    expect(errorMessage(error, "zh")).toBe("该项已被其他玩家认领。");
  });
});

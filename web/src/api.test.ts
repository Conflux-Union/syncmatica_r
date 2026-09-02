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

  it("translates stable server error codes in both languages", () => {
    const error = new ApiError(409, "claim_conflict", "Already claimed");

    expect(errorMessage(error, "en")).toBe("This item is claimed by another player.");
    expect(errorMessage(error, "zh")).toBe("该项已被其他玩家认领。");
  });
});

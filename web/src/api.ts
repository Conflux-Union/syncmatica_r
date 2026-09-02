export type Language = "en" | "zh";

export interface Session {
  authenticated: true;
  playerId: string;
  csrfToken: string;
}

export interface Player {
  id: string;
  name: string;
}

export interface Position {
  dimension: string;
  x: number;
  y: number;
  z: number;
}

export interface ProjectSummary {
  id: string;
  name: string;
  ownerName: string;
  lastModifiedAt: number;
}

export interface ProjectDetail {
  id: string;
  name: string;
  fileName: string;
  hash: string;
  owner: Player;
  lastModifiedBy: Player;
  createdAt: number;
  lastModifiedAt: number;
  position: Position;
  rotation: string;
  mirror: string;
  materialAvailability: string;
}

export interface Material {
  itemId: string;
  translationKey: string;
  fallbackName: string;
  variant: string;
  required: number;
  supplied: number;
  missing: number;
  progressPercent: number;
  claimants: Player[];
}

export interface MaterialSummary extends Omit<Material, "claimants"> {}

export interface StockingArea {
  dimension: string;
  minX: number;
  minY: number;
  minZ: number;
  maxX: number;
  maxY: number;
  maxZ: number;
  volume: number;
}

export interface BuildRegion {
  name: string;
  requiredBlocks: number;
  placedBlocks: number;
  scanned: boolean;
  lastScanAt: number;
  progressPercent: number;
  claimants: Player[];
}

interface Outcome {
  outcome: "claimed" | "released" | "already_claimed" | "already_released" | "updated" | "unchanged";
}

interface ServerError {
  code?: string;
  message?: string;
}

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

const messages: Record<string, Record<Language, string>> = {
  authentication_unavailable: { en: "Authentication is temporarily unavailable.", zh: "身份验证暂时不可用。" },
  claim_conflict: { en: "This item is claimed by another player.", zh: "该项已被其他玩家认领。" },
  cross_site_request: { en: "The request was blocked for security reasons.", zh: "出于安全原因，请求已被阻止。" },
  csrf_failed: { en: "Your session changed. Please sign in again.", zh: "会话已变更，请重新登录。" },
  dimension_not_loaded: { en: "That dimension is not currently loaded.", zh: "该维度当前未加载。" },
  feature_disabled: { en: "This feature is disabled on the server.", zh: "服务器已禁用此功能。" },
  internal_error: { en: "The server encountered an unexpected error.", zh: "服务器遇到意外错误。" },
  invalid_credentials: { en: "Player name or password is incorrect.", zh: "玩家名称或密码错误。" },
  invalid_request: { en: "Some submitted values are invalid.", zh: "提交的部分值无效。" },
  material_not_found: { en: "This material no longer exists.", zh: "该材料已不存在。" },
  not_found: { en: "The requested resource was not found.", zh: "未找到请求的资源。" },
  permission_denied: { en: "You do not have permission to do that.", zh: "你没有执行此操作的权限。" },
  project_not_found: { en: "This project no longer exists.", zh: "该项目已不存在。" },
  rate_limited: { en: "Too many attempts. Please wait and try again.", zh: "尝试次数过多，请稍后重试。" },
  region_not_found: { en: "This build region no longer exists.", zh: "该建造区域已不存在。" },
  request_too_large: { en: "The submitted data is too large.", zh: "提交的数据过大。" },
  server_timeout: { en: "The server did not respond in time.", zh: "服务器未及时响应。" },
  stocking_area_not_found: { en: "No stocking area is configured.", zh: "尚未配置备货区。" },
  stocking_area_too_large: { en: "The stocking area is too large.", zh: "库存区范围过大。" },
  unauthorized: { en: "Please sign in to continue.", zh: "请登录后继续。" },
};

export function errorMessage(error: unknown, language: Language): string {
  if (error instanceof ApiError) {
    return messages[error.code]?.[language] ?? error.message;
  }
  return language === "zh" ? "无法连接到服务器。" : "Could not connect to the server.";
}

export function createApiClient(
  fetcher: typeof fetch = fetch,
  onUnauthorized: () => void = () => undefined,
) {
  let csrfToken = "";

  async function request<T>(
    path: string,
    options: RequestInit = {},
  ): Promise<T> {
    const method = options.method ?? "GET";
    const headers = new Headers(options.headers);
    if (options.body) headers.set("Content-Type", "application/json");
    if (method !== "GET" && method !== "HEAD" && csrfToken) {
      headers.set("X-CSRF-Token", csrfToken);
    }
    const response = await fetcher(`/api/v1${path}`, {
      ...options,
      credentials: "same-origin",
      headers,
      method,
    });
    if (!response.ok) {
      let body: ServerError = {};
      try {
        body = (await response.json()) as ServerError;
      } catch {
        // The status and fallback message still provide a stable error.
      }
      const error = new ApiError(
        response.status,
        body.code ?? "internal_error",
        body.message ?? response.statusText,
      );
      if (response.status === 401) onUnauthorized();
      throw error;
    }
    if (response.status === 204) return undefined as T;
    return response.json() as Promise<T>;
  }

  function rememberSession(value: Session) {
    csrfToken = value.csrfToken;
    return value;
  }

  return {
    async login(name: string, password: string) {
      return rememberSession(await request<Session>("/auth/login", {
        method: "POST",
        body: JSON.stringify({ name, password }),
      }));
    },
    async session(signal?: AbortSignal) {
      return rememberSession(await request<Session>("/auth/session", { signal }));
    },
    logout: () => request<void>("/auth/logout", { method: "POST" }),
    projects: (signal?: AbortSignal) => request<ProjectSummary[]>("/projects", { signal }),
    project: (id: string, signal?: AbortSignal) =>
      request<ProjectDetail>(`/projects/${encodeURIComponent(id)}`, { signal }),
    materialSummary: (signal?: AbortSignal) =>
      request<MaterialSummary[]>("/materials/summary", { signal }),
    materials: (id: string, signal?: AbortSignal) =>
      request<Material[]>(`/projects/${encodeURIComponent(id)}/materials`, { signal }),
    setMaterialClaim: (id: string, itemId: string, variant: string, claimed: boolean) =>
      request<Outcome>(
        `/projects/${encodeURIComponent(id)}/materials/${encodeURIComponent(itemId)}/claim?variant=${encodeURIComponent(variant)}`,
        { method: claimed ? "PUT" : "DELETE" },
      ),
    releaseMaterialClaims: (id: string) =>
      request<Outcome>(`/projects/${encodeURIComponent(id)}/material-claims/me`, {
        method: "DELETE",
      }),
    stockingArea: (id: string, signal?: AbortSignal) =>
      request<StockingArea>(`/projects/${encodeURIComponent(id)}/stocking-area`, { signal }),
    setStockingArea: (id: string, area: Omit<StockingArea, "volume">) =>
      request<Outcome>(`/projects/${encodeURIComponent(id)}/stocking-area`, {
        method: "PUT",
        body: JSON.stringify(area),
      }),
    buildRegions: (id: string, signal?: AbortSignal) =>
      request<BuildRegion[]>(`/projects/${encodeURIComponent(id)}/build-regions`, { signal }),
    setBuildClaim: (id: string, region: string, claimed: boolean) =>
      request<Outcome>(
        `/projects/${encodeURIComponent(id)}/build-regions/${encodeURIComponent(region)}/claim`,
        { method: claimed ? "PUT" : "DELETE" },
      ),
  };
}

export type ApiClient = ReturnType<typeof createApiClient>;

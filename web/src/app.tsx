import {
  Box,
  Boxes,
  ChevronRight,
  FolderKanban,
  Languages,
  ListChecks,
  LogOut,
  Moon,
  PackageCheck,
  Search,
  Sun,
  Warehouse,
} from "lucide-react";
import {
  type FormEvent,
  type ReactNode,
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import {
  Link,
  NavLink,
  Navigate,
  Route,
  Routes,
  useNavigate,
  useParams,
} from "react-router-dom";

import {
  ApiError,
  createApiClient,
  errorMessage,
  type ApiClient,
  type BuildRegion,
  type Language,
  type Material,
  type MaterialSummary,
  type ProjectDetail,
  type ProjectSummary,
  type Session,
  type StockingArea,
} from "./api";
import { Button } from "./components/ui/button";
import { Card } from "./components/ui/card";
import { DataTable } from "./components/ui/data-table";
import { Progress } from "./components/ui/progress";
import { Skeleton } from "./components/ui/skeleton";
import { Tabs } from "./components/ui/tabs";
import { Toast } from "./components/ui/toast";
import itemNamesEn from "./item-names-en.json";
import itemNamesZh from "./item-names-zh.json";
import { usePolling } from "./use-polling";

type Theme = "light" | "dark";
const itemNames: Record<Language, Record<string, string>> = {
  en: itemNamesEn,
  zh: itemNamesZh,
};

const translations = {
  en: {
    appName: "Syncmatica",
    eyebrow: "Server workspace",
    navigationLabel: "Primary navigation",
    projects: "Projects",
    materials: "Material Summary",
    switchLanguage: "切换到中文",
    switchToDarkTheme: "Switch to dark theme",
    switchToLightTheme: "Switch to light theme",
    logout: "Sign out",
    loginTitle: "Welcome back",
    loginDescription: "Sign in with your Minecraft player credentials.",
    playerName: "Player name",
    password: "Password",
    signIn: "Sign in",
    signingIn: "Signing in…",
    projectsDescription: "Browse synchronized building projects.",
    materialsDescription: "Review materials across active projects.",
    noProjects: "No projects yet",
    noProjectsDescription: "Projects synchronized by the server will appear here.",
    noMaterials: "No materials to show",
    noRegions: "No build regions to show",
    filterProjects: "Filter projects",
    filterMaterials: "Filter materials",
    sortProjects: "Sort projects",
    sortMaterials: "Sort materials",
    recent: "Recently modified",
    name: "Name",
    missing: "Missing",
    mostMissing: "Most missing",
    owner: "Owner",
    modified: "Modified",
    item: "Item",
    required: "Required",
    supplied: "Supplied",
    progress: "Progress",
    actions: "Actions",
    tryAgain: "Try again",
    loadingSession: "Loading session",
    loadingProjects: "Loading projects",
    loadingMaterials: "Loading materials",
    loadingProject: "Loading project",
    loadingArea: "Loading stocking area",
    loadingRegions: "Loading build regions",
    projectDetails: "Project details",
    file: "File",
    position: "Position",
    lastEditor: "Last modified by",
    materialTab: "Materials",
    stockingTab: "Stocking Area",
    regionsTab: "Build Regions",
    claimMaterial: "Claim material",
    unclaimMaterial: "Unclaim material",
    unclaimAllMaterials: "Unclaim all materials",
    claimRegion: "Claim region",
    unclaimRegion: "Unclaim region",
    claimants: "Claimed by",
    unclaimed: "Unclaimed",
    dimension: "Dimension",
    overworld: "Overworld",
    nether: "Nether",
    end: "End",
    minX: "Minimum X",
    minY: "Minimum Y",
    minZ: "Minimum Z",
    maxX: "Maximum X",
    maxY: "Maximum Y",
    maxZ: "Maximum Z",
    volume: "Volume",
    saveArea: "Save stocking area",
    saving: "Saving…",
    ownerOnly: "Only the project owner can edit this area.",
    noArea: "No stocking area is configured.",
    invalidBounds: "Minimum coordinates must not exceed maximum coordinates.",
    saved: "Stocking area saved.",
    claimUpdated: "Claim updated.",
    region: "Region",
    blocks: "Blocks",
    scanStatus: "Scan",
    scanned: "Scanned",
    notScanned: "Not scanned",
  },
  zh: {
    appName: "Syncmatica",
    eyebrow: "服务器工作区",
    navigationLabel: "主导航",
    projects: "项目",
    materials: "材料汇总",
    switchLanguage: "Switch to English",
    switchToDarkTheme: "切换到深色主题",
    switchToLightTheme: "切换到浅色主题",
    logout: "退出登录",
    loginTitle: "欢迎回来",
    loginDescription: "使用 Minecraft 玩家凭据登录。",
    playerName: "玩家名称",
    password: "密码",
    signIn: "登录",
    signingIn: "正在登录…",
    projectsDescription: "浏览已同步的建筑项目。",
    materialsDescription: "查看进行中项目的材料。",
    noProjects: "暂无项目",
    noProjectsDescription: "服务器同步的项目将显示在这里。",
    noMaterials: "暂无材料",
    noRegions: "暂无建造区域",
    filterProjects: "筛选项目",
    filterMaterials: "筛选材料",
    sortProjects: "项目排序",
    sortMaterials: "材料排序",
    recent: "最近修改",
    name: "名称",
    missing: "缺少",
    mostMissing: "缺少最多",
    owner: "所有者",
    modified: "修改时间",
    item: "物品",
    required: "需要",
    supplied: "已有",
    progress: "进度",
    actions: "操作",
    tryAgain: "重试",
    loadingSession: "正在加载会话",
    loadingProjects: "正在加载项目",
    loadingMaterials: "正在加载材料",
    loadingProject: "正在加载项目",
    loadingArea: "正在加载库存区",
    loadingRegions: "正在加载建造区域",
    projectDetails: "项目详情",
    file: "文件",
    position: "位置",
    lastEditor: "最后修改者",
    materialTab: "材料",
    stockingTab: "库存区",
    regionsTab: "建造区域",
    claimMaterial: "认领材料",
    unclaimMaterial: "取消认领材料",
    unclaimAllMaterials: "取消全部材料认领",
    claimRegion: "认领区域",
    unclaimRegion: "取消认领区域",
    claimants: "认领者",
    unclaimed: "未认领",
    dimension: "维度",
    overworld: "主世界",
    nether: "下界",
    end: "末地",
    minX: "最小 X",
    minY: "最小 Y",
    minZ: "最小 Z",
    maxX: "最大 X",
    maxY: "最大 Y",
    maxZ: "最大 Z",
    volume: "体积",
    saveArea: "保存库存区",
    saving: "正在保存…",
    ownerOnly: "只有项目所有者可以编辑此区域。",
    noArea: "尚未配置库存区。",
    invalidBounds: "最小坐标不能大于最大坐标。",
    saved: "库存区已保存。",
    claimUpdated: "认领状态已更新。",
    region: "区域",
    blocks: "方块",
    scanStatus: "扫描",
    scanned: "已扫描",
    notScanned: "未扫描",
  },
} as const;

type Copy = (typeof translations)[Language];

function App() {
  const [language, setLanguage] = useState<Language>("en");
  const [theme, setTheme] = useState<Theme>("light");
  const [session, setSession] = useState<Session | null>();
  const navigate = useNavigate();
  const navigateRef = useRef(navigate);
  navigateRef.current = navigate;
  const [api] = useState(() =>
    createApiClient(fetch, () => {
      setSession(null);
      navigateRef.current("/login", { replace: true });
    }),
  );

  useEffect(() => {
    const controller = new AbortController();
    api.session(controller.signal).then(setSession).catch((error) => {
      if (!controller.signal.aborted) setSession(null);
    });
    return () => controller.abort();
  }, [api]);

  const copy = translations[language];
  if (session === undefined) {
    return (
      <div className="session-loader" data-theme={theme}>
        <Skeleton label={copy.loadingSession} />
      </div>
    );
  }

  if (!session) {
    return (
      <div className="login-page" data-theme={theme} lang={language === "zh" ? "zh-CN" : "en"}>
        <PreferenceActions
          copy={copy}
          language={language}
          setLanguage={setLanguage}
          setTheme={setTheme}
          theme={theme}
        />
        <Login api={api} copy={copy} language={language} onLogin={setSession} />
      </div>
    );
  }

  return (
    <div className="app-shell" data-theme={theme} lang={language === "zh" ? "zh-CN" : "en"}>
      <aside className="sidebar">
        <Link className="brand" to="/">
          <span aria-hidden="true" className="brand-mark">S</span>
          <span><strong>{copy.appName}</strong><small>{copy.eyebrow}</small></span>
        </Link>
        <nav aria-label={copy.navigationLabel} className="primary-navigation">
          <NavLink className={({ isActive }) => `navigation-link${isActive ? " navigation-link-active" : ""}`} end to="/">
            <FolderKanban aria-hidden="true" size={19} /><span>{copy.projects}</span>
          </NavLink>
          <NavLink className={({ isActive }) => `navigation-link${isActive ? " navigation-link-active" : ""}`} to="/materials">
            <ListChecks aria-hidden="true" size={19} /><span>{copy.materials}</span>
          </NavLink>
        </nav>
        <div className="shell-actions">
          <PreferenceActions
            copy={copy}
            language={language}
            setLanguage={setLanguage}
            setTheme={setTheme}
            theme={theme}
          />
          <Button
            aria-label={copy.logout}
            onClick={() => void api.logout().finally(() => setSession(null))}
            size="icon"
            variant="ghost"
          >
            <LogOut aria-hidden="true" size={18} />
          </Button>
        </div>
      </aside>
      <main className="main-content">
        <Routes>
          <Route path="/" element={<ProjectsPage api={api} copy={copy} language={language} />} />
          <Route path="/materials" element={<MaterialSummaryPage api={api} copy={copy} language={language} />} />
          <Route path="/projects/:id" element={<ProjectPage api={api} copy={copy} language={language} session={session} />} />
          <Route path="*" element={<Navigate replace to="/" />} />
        </Routes>
      </main>
    </div>
  );
}

function PreferenceActions({
  copy,
  language,
  setLanguage,
  setTheme,
  theme,
}: {
  copy: Copy;
  language: Language;
  setLanguage: (value: Language) => void;
  setTheme: (value: Theme) => void;
  theme: Theme;
}) {
  return (
    <>
      <Button aria-label={copy.switchLanguage} onClick={() => setLanguage(language === "en" ? "zh" : "en")} size="icon" variant="ghost">
        <Languages aria-hidden="true" size={18} />
      </Button>
      <Button
        aria-label={theme === "light" ? copy.switchToDarkTheme : copy.switchToLightTheme}
        onClick={() => setTheme(theme === "light" ? "dark" : "light")}
        size="icon"
        variant="ghost"
      >
        {theme === "light" ? <Moon aria-hidden="true" size={18} /> : <Sun aria-hidden="true" size={18} />}
      </Button>
    </>
  );
}

function Login({
  api,
  copy,
  language,
  onLogin,
}: {
  api: ApiClient;
  copy: Copy;
  language: Language;
  onLogin: (session: Session) => void;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setError("");
    const form = new FormData(event.currentTarget);
    try {
      onLogin(await api.login(String(form.get("name")), String(form.get("password"))));
    } catch (failure) {
      setError(errorMessage(failure, language));
    } finally {
      setBusy(false);
    }
  }
  return (
    <Card className="login-card">
      <div className="login-logo"><PackageCheck aria-hidden="true" size={30} /></div>
      <h1>{copy.loginTitle}</h1>
      <p>{copy.loginDescription}</p>
      <form className="form" onSubmit={submit}>
        <label>{copy.playerName}<input autoComplete="username" name="name" required /></label>
        <label>{copy.password}<input autoComplete="current-password" name="password" required type="password" /></label>
        {error && <p className="form-error" role="alert">{error}</p>}
        <Button disabled={busy} size="default" type="submit" variant="primary">
          {busy ? copy.signingIn : copy.signIn}
        </Button>
      </form>
    </Card>
  );
}

function PageHeading({ description, title }: { description: string; title: string }) {
  return <header className="page-heading"><p>{description}</p><h1>{title}</h1></header>;
}

function ProjectsPage({ api, copy, language }: PageProps) {
  const [projects, setProjects] = useState<ProjectSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [filter, setFilter] = useState("");
  const [sort, setSort] = useState("recent");
  const load = useCallback((signal: AbortSignal) => api.projects(signal), [api]);
  const receive = useCallback((value: ProjectSummary[]) => {
    setProjects(value); setLoading(false); setError("");
  }, []);
  const fail = useCallback((failure: unknown) => {
    setLoading(false); setError(errorMessage(failure, language));
  }, [language]);
  usePolling(load, receive, fail);

  const visible = projects
    .filter((project) => `${project.name} ${project.ownerName}`.toLowerCase().includes(filter.toLowerCase()))
    .sort((a, b) => sort === "name" ? a.name.localeCompare(b.name) : b.lastModifiedAt - a.lastModifiedAt);

  return (
    <>
      <PageHeading description={copy.projectsDescription} title={copy.projects} />
      <Toolbar>
        <label className="search-field"><Search aria-hidden="true" size={18} /><span className="sr-only">{copy.filterProjects}</span>
          <input aria-label={copy.filterProjects} onChange={(event) => setFilter(event.target.value)} value={filter} />
        </label>
        <label className="sr-only" htmlFor="project-sort">{copy.sortProjects}</label>
        <select aria-label={copy.sortProjects} id="project-sort" onChange={(event) => setSort(event.target.value)} value={sort}>
          <option value="recent">{copy.recent}</option><option value="name">{copy.name}</option>
        </select>
      </Toolbar>
      {loading ? <Skeleton label={copy.loadingProjects} /> : error ? <ErrorState error={error} label={copy.tryAgain} onRetry={() => void load(new AbortController().signal).then(receive).catch(fail)} /> :
        visible.length === 0 ? <EmptyState icon={<FolderKanban />} title={copy.noProjects} description={copy.noProjectsDescription} /> :
          <div className="project-grid">{visible.map((project) => (
            <Link className="project-link" data-testid="project-link" key={project.id} to={`/projects/${encodeURIComponent(project.id)}`}>
              <Card>
                <div className="card-icon"><Box aria-hidden="true" /></div>
                <div className="project-card-copy"><h2>{project.name}</h2><p>{copy.owner}: {project.ownerName}</p><small>{copy.modified}: {formatTime(project.lastModifiedAt, language)}</small></div>
                <ChevronRight aria-hidden="true" />
              </Card>
            </Link>
          ))}</div>}
    </>
  );
}

interface PageProps {
  api: ApiClient;
  copy: Copy;
  language: Language;
}

function MaterialSummaryPage({ api, copy, language }: PageProps) {
  const [rows, setRows] = useState<MaterialSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [filter, setFilter] = useState("");
  const [sort, setSort] = useState("missing");
  const load = useCallback((signal: AbortSignal) => api.materialSummary(signal), [api]);
  const receive = useCallback((value: MaterialSummary[]) => { setRows(value); setLoading(false); setError(""); }, []);
  const fail = useCallback((failure: unknown) => { setLoading(false); setError(errorMessage(failure, language)); }, [language]);
  usePolling(load, receive, fail);
  const visible = rows
    .filter((row) =>
      `${materialName(row, language)} ${row.itemId} ${row.variant}`
        .toLowerCase()
        .includes(filter.toLowerCase()))
    .sort((a, b) => sort === "name"
      ? materialName(a, language).localeCompare(materialName(b, language), language === "zh" ? "zh-CN" : "en")
      : b.missing - a.missing);
  return (
    <>
      <PageHeading description={copy.materialsDescription} title={copy.materials} />
      <Toolbar>
        <label className="search-field"><Search aria-hidden="true" size={18} /><span className="sr-only">{copy.filterMaterials}</span>
          <input aria-label={copy.filterMaterials} onChange={(event) => setFilter(event.target.value)} value={filter} />
        </label>
        <select aria-label={copy.sortMaterials} onChange={(event) => setSort(event.target.value)} value={sort}>
          <option value="missing">{copy.mostMissing}</option><option value="name">{copy.name}</option>
        </select>
      </Toolbar>
      {loading ? <Skeleton label={copy.loadingMaterials} /> : error ? <ErrorState error={error} label={copy.tryAgain} onRetry={() => void load(new AbortController().signal).then(receive).catch(fail)} /> :
        visible.length === 0 ? <EmptyState icon={<ListChecks />} title={copy.noMaterials} /> :
          <MaterialTable copy={copy} language={language} rows={visible} />}
    </>
  );
}

function MaterialTable({ copy, language, rows, session, onClaim }: {
  copy: Copy;
  language: Language;
  rows: (Material | MaterialSummary)[];
  session?: Session;
  onClaim?: (row: Material, claim: boolean) => void;
}) {
  return (
    <DataTable label={copy.materials}>
      <thead><tr><th>{copy.item}</th><th>{copy.required}</th><th>{copy.supplied}</th><th>{copy.missing}</th><th>{copy.progress}</th>{onClaim && <th>{copy.actions}</th>}</tr></thead>
      <tbody>{rows.map((row) => {
        const material = "claimants" in row ? row : undefined;
        const mine = material?.claimants.some((player) => player.id === session?.playerId) ?? false;
        return <tr key={`${row.itemId}\0${row.variant}`}>
          <td><strong>{materialName(row, language)}</strong>{row.variant && <small>{row.variant}</small>}{material && <small>{material.claimants.length ? `${copy.claimants}: ${material.claimants.map((player) => player.name).join(", ")}` : copy.unclaimed}</small>}</td>
          <td>{row.required.toLocaleString()}</td><td>{row.supplied.toLocaleString()}</td><td>{row.missing.toLocaleString()}</td>
          <td><div className="progress-cell"><Progress label={`${row.progressPercent}%`} value={row.progressPercent} /><span>{row.progressPercent}%</span></div></td>
          {onClaim && material && <td><Button onClick={() => onClaim(material, !mine)} size="default" variant={mine ? "outline" : "primary"}>{mine ? copy.unclaimMaterial : copy.claimMaterial}</Button></td>}
        </tr>;
      })}</tbody>
    </DataTable>
  );
}

function ProjectPage({ api, copy, language, session }: PageProps & { session: Session }) {
  const { id = "" } = useParams();
  const [project, setProject] = useState<ProjectDetail>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [tab, setTab] = useState("materials");
  const load = useCallback((signal: AbortSignal) => api.project(id, signal), [api, id]);
  const receive = useCallback((value: ProjectDetail) => { setProject(value); setLoading(false); setError(""); }, []);
  const fail = useCallback((failure: unknown) => { setLoading(false); setError(errorMessage(failure, language)); }, [language]);
  usePolling(load, receive, fail);
  if (loading) return <Skeleton label={copy.loadingProject} />;
  if (error || !project) return <ErrorState error={error} label={copy.tryAgain} onRetry={() => void load(new AbortController().signal).then(receive).catch(fail)} />;
  return (
    <>
      <Link className="back-link" to="/">← {copy.projects}</Link>
      <PageHeading description={copy.projectDetails} title={project.name} />
      <Card className="detail-strip">
        <Detail label={copy.owner} value={project.owner.name} />
        <Detail label={copy.file} value={project.fileName} />
        <Detail label={copy.position} value={`${dimensionName(project.position.dimension, copy)} · ${project.position.x}, ${project.position.y}, ${project.position.z}`} />
        <Detail label={copy.lastEditor} value={project.lastModifiedBy.name} />
      </Card>
      <Tabs active={tab} onChange={setTab} options={[
        { id: "materials", label: copy.materialTab },
        { id: "stocking", label: copy.stockingTab },
        { id: "regions", label: copy.regionsTab },
      ]}>
        {tab === "materials" && <ProjectMaterials api={api} copy={copy} id={id} language={language} session={session} />}
        {tab === "stocking" && <StockingAreaPanel api={api} copy={copy} id={id} language={language} owner={project.owner.id === session.playerId} positionDimension={project.position.dimension} />}
        {tab === "regions" && <BuildRegionsPanel api={api} copy={copy} id={id} language={language} session={session} />}
      </Tabs>
    </>
  );
}

function ProjectMaterials({ api, copy, id, language, session }: PageProps & { id: string; session: Session }) {
  const [rows, setRows] = useState<Material[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [toast, setToast] = useState<string | null>(null);
  const load = useCallback((signal: AbortSignal) => api.materials(id, signal), [api, id]);
  const receive = useCallback((value: Material[]) => { setRows(value); setLoading(false); setError(""); }, []);
  const fail = useCallback((failure: unknown) => { setLoading(false); setError(errorMessage(failure, language)); }, [language]);
  usePolling(load, receive, fail);
  async function claim(row: Material, value: boolean) {
    try {
      await api.setMaterialClaim(id, row.itemId, row.variant, value);
      setToast(copy.claimUpdated);
      receive(await api.materials(id));
    } catch (failure) {
      setToast(errorMessage(failure, language));
    }
  }
  async function releaseAll() {
    try {
      await api.releaseMaterialClaims(id);
      setToast(copy.claimUpdated);
      receive(await api.materials(id));
    } catch (failure) {
      setToast(errorMessage(failure, language));
    }
  }
  const hasOwnClaims = rows.some((row) =>
    row.claimants.some((player) => player.id === session.playerId));
  return <section className="tab-section">{loading ? <Skeleton label={copy.loadingMaterials} /> : error ? <ErrorState error={error} label={copy.tryAgain} onRetry={() => void load(new AbortController().signal).then(receive).catch(fail)} /> : rows.length ? <><div className="tab-actions">{hasOwnClaims && <Button onClick={() => void releaseAll()} size="default" variant="outline">{copy.unclaimAllMaterials}</Button>}</div><MaterialTable copy={copy} language={language} onClaim={(row, value) => void claim(row, value)} rows={rows} session={session} /></> : <EmptyState icon={<ListChecks />} title={copy.noMaterials} />}<Toast message={toast} /></section>;
}

type AreaDraft = Omit<StockingArea, "volume">;
const coordinateKeys = ["minX", "minY", "minZ", "maxX", "maxY", "maxZ"] as const;

function StockingAreaPanel({ api, copy, id, language, owner, positionDimension }: PageProps & { id: string; owner: boolean; positionDimension: string }) {
  const empty = useMemo<AreaDraft>(() => ({ dimension: positionDimension, minX: 0, minY: 0, minZ: 0, maxX: 0, maxY: 0, maxZ: 0 }), [positionDimension]);
  const [area, setArea] = useState<StockingArea>();
  const [draft, setDraft] = useState<AreaDraft>(empty);
  const [dirty, setDirty] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [toast, setToast] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const load = useCallback((signal: AbortSignal) => api.stockingArea(id, signal), [api, id]);
  const receive = useCallback((value: StockingArea) => {
    setArea(value); setLoading(false); setError("");
    if (!dirty) setDraft(({ ...value }));
  }, [dirty]);
  const fail = useCallback((failure: unknown) => {
    setLoading(false);
    if (failure instanceof ApiError && failure.code === "stocking_area_not_found") {
      setArea(undefined); setError("");
    } else setError(errorMessage(failure, language));
  }, [language]);
  usePolling(load, receive, fail);
  async function submit(event: FormEvent) {
    event.preventDefault();
    if (draft.minX > draft.maxX || draft.minY > draft.maxY || draft.minZ > draft.maxZ) {
      setToast(copy.invalidBounds); return;
    }
    setSaving(true);
    try {
      await api.setStockingArea(id, draft);
      setDirty(false); setToast(copy.saved);
      receive(await api.stockingArea(id));
    } catch (failure) {
      setToast(errorMessage(failure, language));
    } finally {
      setSaving(false);
    }
  }
  if (loading) return <section className="tab-section"><Skeleton label={copy.loadingArea} /></section>;
  if (error) return <section className="tab-section"><ErrorState error={error} label={copy.tryAgain} onRetry={() => void load(new AbortController().signal).then(receive).catch(fail)} /></section>;
  return (
    <section className="tab-section">
      {!owner && <p className="notice"><Warehouse aria-hidden="true" />{copy.ownerOnly}</p>}
      {owner ? <form className="coordinate-form" onSubmit={submit}>
        <label className="dimension-field">{copy.dimension} · {dimensionName(draft.dimension, copy)}<input onChange={(event) => { setDirty(true); setDraft({ ...draft, dimension: event.target.value }); }} required value={draft.dimension} /></label>
        <div className="coordinate-grid">{coordinateKeys.map((key) => <label key={key}>{copy[key]}<input inputMode="numeric" onChange={(event) => { setDirty(true); setDraft({ ...draft, [key]: Number(event.target.value) }); }} required type="number" value={draft[key]} /></label>)}</div>
        {area && <p className="volume">{copy.volume}: {area.volume.toLocaleString()}</p>}
        <Button disabled={saving} size="default" type="submit" variant="primary">{saving ? copy.saving : copy.saveArea}</Button>
      </form> : area ? <AreaReadOnly area={area} copy={copy} /> : <EmptyState icon={<Warehouse />} title={copy.noArea} />}
      <Toast message={toast} />
    </section>
  );
}

function AreaReadOnly({ area, copy }: { area: StockingArea; copy: Copy }) {
  return <Card className="area-readonly"><strong>{dimensionName(area.dimension, copy)}</strong><span>{area.minX}, {area.minY}, {area.minZ} → {area.maxX}, {area.maxY}, {area.maxZ}</span><small>{copy.volume}: {area.volume.toLocaleString()}</small></Card>;
}

function BuildRegionsPanel({ api, copy, id, language, session }: PageProps & { id: string; session: Session }) {
  const [rows, setRows] = useState<BuildRegion[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [toast, setToast] = useState<string | null>(null);
  const load = useCallback((signal: AbortSignal) => api.buildRegions(id, signal), [api, id]);
  const receive = useCallback((value: BuildRegion[]) => { setRows(value); setLoading(false); setError(""); }, []);
  const fail = useCallback((failure: unknown) => { setLoading(false); setError(errorMessage(failure, language)); }, [language]);
  usePolling(load, receive, fail);
  async function claim(row: BuildRegion, value: boolean) {
    try {
      await api.setBuildClaim(id, row.name, value);
      setToast(copy.claimUpdated);
      receive(await api.buildRegions(id));
    } catch (failure) {
      setToast(errorMessage(failure, language));
    }
  }
  return (
    <section className="tab-section">
      {loading ? <Skeleton label={copy.loadingRegions} /> : error ? <ErrorState error={error} label={copy.tryAgain} onRetry={() => void load(new AbortController().signal).then(receive).catch(fail)} /> : rows.length === 0 ? <EmptyState icon={<Boxes />} title={copy.noRegions} /> :
        <DataTable label={copy.regionsTab}><thead><tr><th>{copy.region}</th><th>{copy.blocks}</th><th>{copy.progress}</th><th>{copy.scanStatus}</th><th>{copy.actions}</th></tr></thead>
          <tbody>{rows.map((row) => {
            const mine = row.claimants.some((player) => player.id === session.playerId);
            return <tr key={row.name}><td><strong>{row.name}</strong><small>{row.claimants.length ? `${copy.claimants}: ${row.claimants.map((player) => player.name).join(", ")}` : copy.unclaimed}</small></td><td>{row.placedBlocks.toLocaleString()} / {row.requiredBlocks.toLocaleString()}</td><td><div className="progress-cell"><Progress label={`${row.progressPercent}%`} value={row.progressPercent} /><span>{row.progressPercent}%</span></div></td><td>{row.scanned ? copy.scanned : copy.notScanned}</td><td><Button onClick={() => void claim(row, !mine)} size="default" variant={mine ? "outline" : "primary"}>{mine ? copy.unclaimRegion : copy.claimRegion}</Button></td></tr>;
          })}</tbody></DataTable>}
      <Toast message={toast} />
    </section>
  );
}

function Toolbar({ children }: { children: ReactNode }) {
  return <div className="toolbar">{children}</div>;
}

function EmptyState({ description, icon, title }: { description?: string; icon: ReactNode; title: string }) {
  return <Card className="empty-state"><div className="empty-icon">{icon}</div><h2>{title}</h2>{description && <p>{description}</p>}</Card>;
}

function ErrorState({ error, label, onRetry }: { error: string; label: string; onRetry: () => void }) {
  return <Card className="error-state"><p role="alert">{error}</p><Button onClick={onRetry} size="default" variant="outline">{label}</Button></Card>;
}

function Detail({ label, value }: { label: string; value: string }) {
  return <div><small>{label}</small><strong>{value}</strong></div>;
}

function formatTime(value: number, language: Language) {
  return new Intl.DateTimeFormat(language === "zh" ? "zh-CN" : "en", { dateStyle: "medium" }).format(new Date(value));
}

function materialName(row: Material | MaterialSummary, language: Language) {
  return itemNames[language][row.translationKey] ?? row.fallbackName;
}

function dimensionName(id: string, copy: Copy) {
  if (id === "minecraft:overworld") return copy.overworld;
  if (id === "minecraft:the_nether") return copy.nether;
  if (id === "minecraft:the_end") return copy.end;
  const path = id.split(":").pop() ?? id;
  return path.replace(/[_-]+/g, " ").replace(/\b\w/g, (letter) => letter.toUpperCase());
}

export { App, translations };

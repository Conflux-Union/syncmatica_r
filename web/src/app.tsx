import { FolderKanban, Languages, ListChecks } from "lucide-react";
import { useState } from "react";
import { NavLink, useLocation } from "react-router-dom";

import { Button } from "./components/ui/button";

const translations = {
  en: {
    appName: "Syncmatica",
    eyebrow: "Server workspace",
    navigationLabel: "Primary navigation",
    projects: "Projects",
    materials: "Material Summary",
    switchLanguage: "切换到中文",
    projectsDescription: "Browse synchronized building projects.",
    materialsDescription: "Review materials across active projects.",
  },
  zh: {
    appName: "Syncmatica",
    eyebrow: "服务器工作区",
    navigationLabel: "主导航",
    projects: "项目",
    materials: "材料汇总",
    switchLanguage: "Switch to English",
    projectsDescription: "浏览已同步的建筑项目。",
    materialsDescription: "查看进行中项目的材料。",
  },
} as const;

type Language = keyof typeof translations;

const navigation = [
  { key: "projects", href: "/", icon: FolderKanban },
  { key: "materials", href: "/materials", icon: ListChecks },
] as const;

function App() {
  const [language, setLanguage] = useState<Language>("en");
  const location = useLocation();
  const copy = translations[language];
  const materialView = location.pathname === "/materials";

  function toggleLanguage() {
    setLanguage((current) => (current === "en" ? "zh" : "en"));
  }

  return (
    <div className="app-shell" lang={language === "en" ? "en" : "zh-CN"}>
      <aside className="sidebar">
        <div className="brand">
          <span className="brand-mark" aria-hidden="true">
            S
          </span>
          <span>
            <strong>{copy.appName}</strong>
            <small>{copy.eyebrow}</small>
          </span>
        </div>

        <nav aria-label={copy.navigationLabel} className="primary-navigation">
          {navigation.map(({ key, href, icon: Icon }) => (
            <NavLink
              className={({ isActive }) =>
                `navigation-link${isActive ? " navigation-link-active" : ""}`
              }
              end={href === "/"}
              key={key}
              to={href}
            >
              <Icon aria-hidden="true" size={19} strokeWidth={1.8} />
              <span>{copy[key]}</span>
            </NavLink>
          ))}
        </nav>

        <Button
          aria-label={copy.switchLanguage}
          className="language-button"
          onClick={toggleLanguage}
          type="button"
        >
          <Languages aria-hidden="true" size={18} />
        </Button>
      </aside>

      <main className="main-content">
        <div className="page-heading">
          <p>{copy.eyebrow}</p>
          <h1>{materialView ? copy.materials : copy.projects}</h1>
          <span>
            {materialView
              ? copy.materialsDescription
              : copy.projectsDescription}
          </span>
        </div>
      </main>
    </div>
  );
}

export { App, translations };

import {
  FolderKanban,
  Languages,
  ListChecks,
  Moon,
  Sun,
} from "lucide-react";
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
    switchToDarkTheme: "Switch to dark theme",
    switchToLightTheme: "Switch to light theme",
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
    switchToDarkTheme: "切换到深色主题",
    switchToLightTheme: "切换到浅色主题",
    projectsDescription: "浏览已同步的建筑项目。",
    materialsDescription: "查看进行中项目的材料。",
  },
} as const;

type Language = keyof typeof translations;
type Theme = "light" | "dark";

const navigation = [
  { key: "projects", href: "/", icon: FolderKanban },
  { key: "materials", href: "/materials", icon: ListChecks },
] as const;

function App() {
  const [language, setLanguage] = useState<Language>("en");
  const [theme, setTheme] = useState<Theme>("light");
  const location = useLocation();
  const copy = translations[language];
  const materialView = location.pathname === "/materials";

  function toggleLanguage() {
    setLanguage((current) => (current === "en" ? "zh" : "en"));
  }

  function toggleTheme() {
    setTheme((current) => (current === "light" ? "dark" : "light"));
  }

  return (
    <div
      className="app-shell"
      data-theme={theme}
      lang={language === "en" ? "en" : "zh-CN"}
    >
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

        <div className="shell-actions">
          <Button
            aria-label={copy.switchLanguage}
            className="language-button"
            onClick={toggleLanguage}
            type="button"
          >
            <Languages aria-hidden="true" size={18} />
          </Button>
          <Button
            aria-label={
              theme === "light"
                ? copy.switchToDarkTheme
                : copy.switchToLightTheme
            }
            className="theme-button"
            onClick={toggleTheme}
            type="button"
          >
            {theme === "light" ? (
              <Moon aria-hidden="true" size={18} />
            ) : (
              <Sun aria-hidden="true" size={18} />
            )}
          </Button>
        </div>
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

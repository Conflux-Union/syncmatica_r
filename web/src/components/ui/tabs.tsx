import type { ReactNode } from "react";

export interface TabOption {
  id: string;
  label: string;
}

export function Tabs({
  active,
  children,
  onChange,
  options,
}: {
  active: string;
  children: ReactNode;
  onChange: (id: string) => void;
  options: TabOption[];
}) {
  return (
    <>
      <div className="tabs" role="tablist">
        {options.map((option) => (
          <button
            aria-selected={active === option.id}
            className={active === option.id ? "active" : ""}
            key={option.id}
            onClick={() => onChange(option.id)}
            role="tab"
            type="button"
          >
            {option.label}
          </button>
        ))}
      </div>
      <div role="tabpanel">{children}</div>
    </>
  );
}

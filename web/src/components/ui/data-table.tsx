import type { ReactNode } from "react";

export function DataTable({
  children,
  label,
}: {
  children: ReactNode;
  label: string;
}) {
  return (
    <div className="table-scroll">
      <table aria-label={label}>{children}</table>
    </div>
  );
}

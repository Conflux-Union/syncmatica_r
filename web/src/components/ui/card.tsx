import type { HTMLAttributes } from "react";

export function Card(props: HTMLAttributes<HTMLDivElement>) {
  return <div {...props} className={`card ${props.className ?? ""}`} />;
}

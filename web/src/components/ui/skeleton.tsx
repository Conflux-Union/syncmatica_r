export function Skeleton({ label }: { label: string }) {
  return (
    <div aria-label={label} className="skeleton-stack" role="status">
      <span />
      <span />
      <span />
    </div>
  );
}

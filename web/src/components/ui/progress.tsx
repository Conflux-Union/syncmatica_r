export function Progress({ value, label }: { value: number; label: string }) {
  const safe = Math.max(0, Math.min(100, value));
  return (
    <div className="progress" aria-label={label} aria-valuemax={100} aria-valuemin={0} aria-valuenow={safe} role="progressbar">
      <span style={{ width: `${safe}%` }} />
    </div>
  );
}

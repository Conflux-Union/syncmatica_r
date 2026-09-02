import { useEffect, useRef } from "react";

export function usePolling<T>(
  load: (signal: AbortSignal) => Promise<T>,
  receive: (value: T) => void,
  fail: (error: unknown) => void,
  enabled = true,
) {
  const receiveRef = useRef(receive);
  const failRef = useRef(fail);
  receiveRef.current = receive;
  failRef.current = fail;

  useEffect(() => {
    if (!enabled) return;
    let controller: AbortController | undefined;
    let timer: number | undefined;
    let disposed = false;

    const schedule = () => {
      window.clearTimeout(timer);
      if (!disposed && document.visibilityState !== "hidden") {
        timer = window.setTimeout(run, 5_000);
      }
    };

    const run = async () => {
      if (disposed || document.visibilityState === "hidden") return;
      controller?.abort();
      controller = new AbortController();
      try {
        const value = await load(controller.signal);
        if (!disposed && !controller.signal.aborted) receiveRef.current(value);
      } catch (error) {
        if (!disposed && !controller.signal.aborted) failRef.current(error);
      } finally {
        schedule();
      }
    };

    const visibility = () => {
      if (document.visibilityState === "hidden") {
        window.clearTimeout(timer);
        controller?.abort();
      } else {
        void run();
      }
    };

    void run();
    document.addEventListener("visibilitychange", visibility);
    return () => {
      disposed = true;
      window.clearTimeout(timer);
      controller?.abort();
      document.removeEventListener("visibilitychange", visibility);
    };
  }, [enabled, load]);
}

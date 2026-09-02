import { readFileSync } from "node:fs";
import { resolve } from "node:path";

import { describe, expect, it } from "vitest";

const styles = readFileSync(resolve("src/index.css"), "utf8");

describe("shell accessibility styles", () => {
  it("defines dark theme tokens", () => {
    expect(styles).toMatch(
      /\[data-theme="dark"\]\s*{[^}]*color-scheme:\s*dark;[^}]*--background:/s,
    );
  });

  it("removes transitions when reduced motion is preferred", () => {
    expect(styles).toMatch(
      /@media\s*\(prefers-reduced-motion:\s*reduce\)\s*{[^}]*\.navigation-link[^}]*transition:\s*none;/s,
    );
  });

  it("gives navigation links a minimum 44 pixel target", () => {
    const navigationBlock = styles.match(
      /\.navigation-link\s*{([^}]*)}/s,
    )?.[1];

    expect(navigationBlock).toMatch(/min-height:\s*44px;/);
    expect(navigationBlock).toMatch(/min-width:\s*44px;/);
  });
});

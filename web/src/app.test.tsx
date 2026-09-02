import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it } from "vitest";

import { App } from "./app";

describe("App", () => {
  it("provides keyboard-accessible links to the main sections", async () => {
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <App />
      </MemoryRouter>,
    );

    const navigation = screen.getByRole("navigation", {
      name: "Primary navigation",
    });
    const projects = within(navigation).getByRole("link", {
      name: "Projects",
    });
    const materialSummary = within(navigation).getByRole("link", {
      name: "Material Summary",
    });

    expect(projects).toHaveAttribute("href", "/");
    expect(materialSummary).toHaveAttribute("href", "/materials");

    await user.tab();
    expect(projects).toHaveFocus();
    await user.tab();
    expect(materialSummary).toHaveFocus();
  });
});

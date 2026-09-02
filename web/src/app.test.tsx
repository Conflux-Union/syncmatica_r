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

  it("lets the user switch between light and dark themes", async () => {
    const user = userEvent.setup();
    const { container } = render(
      <MemoryRouter>
        <App />
      </MemoryRouter>,
    );

    const shell = container.querySelector(".app-shell");
    const themeButton = screen.getByRole("button", {
      name: "Switch to dark theme",
    });

    expect(shell).toHaveAttribute("data-theme", "light");
    expect(themeButton).toHaveClass("size-11");

    await user.click(themeButton);

    expect(shell).toHaveAttribute("data-theme", "dark");
    expect(
      screen.getByRole("button", { name: "Switch to light theme" }),
    ).toBeInTheDocument();
  });
});

/// <reference types="@testing-library/jest-dom" />
/// <reference types="@testing-library/jest-dom/vitest" />
import "@testing-library/jest-dom";
import { createElement, type ReactNode } from "react";
import { vi } from "vitest";
import { I18nProvider } from "../i18n";

// Every screen/component reads copy through useI18n, so wrap the shared
// render helper once instead of restating the provider in each test file.
vi.mock("@testing-library/react", async (importOriginal) => {
    const actual = await importOriginal<typeof import("@testing-library/react")>();
    return {
        ...actual,
        render: (ui: ReactNode, options?: Parameters<typeof actual.render>[1]) => {
            const result = actual.render(createElement(I18nProvider, null, ui), options);
            const wrappedRerender = (next: ReactNode) =>
                result.rerender(createElement(I18nProvider, null, next));
            return { ...result, rerender: wrappedRerender };
        },
    };
});

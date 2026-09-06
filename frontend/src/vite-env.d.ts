/// <reference types="vite/client" />

// Side-effect grammar registrations have no typings.
declare module "monaco-editor/esm/vs/basic-languages/*" {
    const contribution: unknown;
    export default contribution;
}

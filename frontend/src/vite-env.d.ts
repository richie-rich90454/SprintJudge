/// <reference types="vite/client" />

// Side-effect monaco modules (feature set, grammar registrations) ship no typings.
declare module "monaco-editor/esm/vs/editor/edcore.main" {
    const _: unknown;
    export default _;
}
declare module "monaco-editor/esm/vs/basic-languages/*" {
    const contribution: unknown;
    export default contribution;
}

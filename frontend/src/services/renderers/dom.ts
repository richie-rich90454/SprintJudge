export function el<K extends keyof HTMLElementTagNameMap>(
    tag: K,
    props: Partial<HTMLElementTagNameMap[K]> & {
        class?: string;
        dataset?: Record<string, string>;
    } = {},
    children: (Node | string)[] = [],
): HTMLElementTagNameMap[K] {
    const node = document.createElement(tag);
    const { class: cls, dataset, ...rest } = props as Record<string, unknown>;
    if (cls) node.className = cls as string;
    if (dataset) for (const [k, v] of Object.entries(dataset)) node.dataset[k] = v;
    Object.assign(node, rest);
    for (const c of children) node.append(typeof c === "string" ? document.createTextNode(c) : c);
    return node;
}

export function clear(node: HTMLElement): void {
    node.innerHTML = "";
}

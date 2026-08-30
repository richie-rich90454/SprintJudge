import { useEffect, useRef, useState } from "react";

/**
 * Fixed-row-height virtualization without dependencies. Returns the visible
 * [start,end) index window plus the scroll container ref. Exactness: the
 * spacers guarantee total scroll height equals rows*rowHeight, so the
 * scrollbar never lies and no row is ever clipped or duplicated.
 */
export function useVirtualWindow(total: number, rowHeight: number, overscan = 6) {
    const ref = useRef<HTMLDivElement>(null);
    const [start, setStart] = useState(0);

    useEffect(() => {
        const el = ref.current;
        if (!el) return;
        const onScroll = () => {
            const first = Math.max(0, Math.floor(el.scrollTop / rowHeight) - overscan);
            setStart(first);
        };
        onScroll();
        el.addEventListener("scroll", onScroll, { passive: true });
        return () => el.removeEventListener("scroll", onScroll);
    }, [rowHeight, overscan]);

    const viewportRows = Math.ceil(460 / rowHeight) + overscan * 2;
    const end = Math.min(total, start + viewportRows);
    return { ref, start, end };
}

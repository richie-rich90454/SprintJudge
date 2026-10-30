import { HTMLAttributes, ReactNode } from "react";

interface CardProps extends HTMLAttributes<HTMLDivElement> {
    children: ReactNode;
}

/** Flat surface card. */
export function Card({ children, className = "", ...rest }: CardProps) {
    return (
        <div className={`card ${className}`.trim()} {...rest}>
            {children}
        </div>
    );
}

import { ButtonHTMLAttributes, forwardRef } from "react";

type Variant = "primary" | "secondary" | "danger" | "ghost";
type Size = "sm" | "md" | "lg" | "icon";

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
    variant?: Variant;
    size?: Size;
}

const VARIANTS: Record<Variant, string> = {
    primary: "btn-primary",
    secondary: "btn-secondary",
    danger: "btn-danger",
    ghost: "btn-ghost",
};

const SIZES: Record<Size, string> = {
    sm: "btn-sm",
    md: "",
    lg: "btn-lg",
    icon: "btn-icon",
};

/** Flat design-system button. Plain <button> — no component-library dependency. */
export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
    { variant = "primary", size = "md", className = "", type = "button", ...rest },
    ref,
) {
    return (
        <button
            ref={ref}
            type={type}
            className={`btn ${VARIANTS[variant]} ${SIZES[size]} ${className}`.trim()}
            {...rest}
        />
    );
});

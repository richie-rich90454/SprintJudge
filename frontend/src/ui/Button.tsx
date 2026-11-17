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
    danger: "btn-error",
    ghost: "btn-ghost",
};

const SIZES: Record<Size, string> = {
    sm: "btn-sm",
    md: "",
    lg: "btn-lg",
    icon: "btn-square",
};

/** DaisyUI button primitive, themed by the SprintJudge daisy theme in index.css. */
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

import { InputHTMLAttributes, forwardRef } from "react";

/** Single-line text input bound to the flat input style. */
export const TextInput = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement>>(
    function TextInput({ className = "", ...rest }, ref) {
        return <input ref={ref} className={`input-underline ${className}`.trim()} {...rest} />;
    },
);

import { Component, type ReactNode } from "react";

interface Props {
    children: ReactNode;
    fallback?: ReactNode;
}

interface State {
    hasError: boolean;
    error: Error | null;
}

export class ErrorBoundary extends Component<Props, State> {
    state: State = { hasError: false, error: null };

    static getDerivedStateFromError(error: Error): State {
        return { hasError: true, error };
    }

    render() {
        if (this.state.hasError) {
            return (
                this.props.fallback ?? (
                    <div className="p-6 text-center">
                        <p className="text-lg font-bold text-[var(--oq-danger)]">
                            Something went wrong
                        </p>
                        <p className="text-[var(--oq-ink-soft)] mt-2 text-sm">
                            {this.state.error?.message ?? "An unexpected error occurred."}
                        </p>
                    </div>
                )
            );
        }
        return this.props.children;
    }
}

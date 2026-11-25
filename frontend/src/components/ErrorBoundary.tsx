import { Component, type ReactNode } from "react";
import { detectLocale } from "../i18n";

interface Props {
    children: ReactNode;
    fallback?: ReactNode;
}

interface State {
    hasError: boolean;
    error: Error | null;
}

const COPY = {
    en: {
        title: "Something went wrong",
        message: "This screen hit a problem. Try again, or reload to pick up where you left off.",
        retry: "Try again",
        reload: "Reload",
    },
    ar: {
        title: "حدث خطأ ما",
        message: "واجهت هذه الشاشة مشكلة. حاول مرة أخرى أو أعد التحميل للعودة إلى حيث توقفت.",
        retry: "حاول مرة أخرى",
        reload: "إعادة التحميل",
    },
} as const;

export class ErrorBoundary extends Component<Props, State> {
    state: State = { hasError: false, error: null };

    static getDerivedStateFromError(error: Error): State {
        return { hasError: true, error };
    }

    private retry = () => this.setState({ hasError: false, error: null });

    render() {
        if (this.state.hasError) {
            if (this.props.fallback) return this.props.fallback;
            const copy = COPY[detectLocale()];
            return (
                <div className="p-6 text-center">
                    <p className="text-lg font-bold text-[var(--oq-danger)]">{copy.title}</p>
                    <p className="text-[var(--oq-ink-soft)] mt-2 text-sm">{copy.message}</p>
                    <button
                        type="button"
                        onClick={this.retry}
                        className="btn-primary mt-4 min-h-[44px] px-5"
                    >
                        {copy.retry}
                    </button>
                    <button
                        type="button"
                        onClick={() => window.location.reload()}
                        className="btn btn-ghost mt-4 min-h-[44px] px-5"
                    >
                        {copy.reload}
                    </button>
                </div>
            );
        }
        return this.props.children;
    }
}

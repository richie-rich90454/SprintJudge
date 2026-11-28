import { useI18n } from "../i18n";
import { useEffect, useState } from "react";
import { Link } from "@tanstack/react-router";
import { Card } from "../ui/Card";
import { Button } from "../ui/Button";
import { TextInput } from "../ui/TextInput";
import { Shell } from "../components/Shell";
import { LogoMark } from "../components/LogoMark";

interface AuthProvider {
    id: string;
    name: string;
    authorizationUrl: string;
}

export function AdminLoginView() {
    const { t } = useI18n();
    const [providers, setProviders] = useState<AuthProvider[]>([]);
    const loginFailed = new URLSearchParams(window.location.search).has("error");
    useEffect(() => {
        let active = true;
        fetch("/api/public/auth/providers")
            .then((response) => (response.ok ? response.json() : []))
            .then((data: AuthProvider[]) => {
                if (active) setProviders(data);
            })
            .catch(() => {
                if (active) setProviders([]);
            });
        return () => {
            active = false;
        };
    }, []);

    return (
        <Shell minimal>
            <div className="flex-1 flex items-center justify-center p-4">
                <Card className="w-full max-w-sm">
                    <div className="p-6">
                        <div className="flex items-center gap-2.5 mb-6">
                            <LogoMark size={28} />
                            <span className="font-extrabold tracking-tight">
                                {t("ui.sprintjudgeAdmin")}
                            </span>
                        </div>
                        <form method="POST" action="/admin/login" className="flex flex-col gap-4">
                            <label className="label-caps block mb-1" htmlFor="al-user">
                                {t("ui.username")}
                            </label>
                            <TextInput
                                id="al-user"
                                name="username"
                                placeholder={t("ui.adminPlaceholder")}
                                autoComplete="username"
                                required
                                aria-invalid={loginFailed}
                            />
                            <label className="label-caps block mb-1" htmlFor="al-pass">
                                {t("ui.password")}
                            </label>
                            <TextInput
                                id="al-pass"
                                name="password"
                                type="password"
                                placeholder={t("ui.passwordPlaceholder")}
                                autoComplete="current-password"
                                required
                                aria-invalid={loginFailed}
                            />
                            {loginFailed && (
                                <p
                                    id="al-error"
                                    role="alert"
                                    className="text-[var(--oq-danger)] text-sm"
                                >
                                    {t("ui.wrongUsernameOrPasswordPleaseTryAgain")}
                                </p>
                            )}
                            <Button type="submit" variant="primary" className="w-full">
                                {t("ui.signIn")}
                            </Button>
                            <Link to="/" className="btn btn-secondary w-full">
                                {t("ui.backToPlayerView")}
                            </Link>
                        </form>
                        {providers.length > 0 && (
                            <div className="border-t border-[var(--oq-border)] px-6 pb-6">
                                <p className="label-caps mb-3">{t("ui.orSignInWith")}</p>
                                <div className="flex flex-col gap-2">
                                    {providers.map((provider) => (
                                        <a
                                            key={provider.id}
                                            href={provider.authorizationUrl}
                                            className="btn btn-secondary w-full"
                                        >
                                            {provider.name}
                                        </a>
                                    ))}
                                </div>
                            </div>
                        )}
                    </div>
                </Card>
            </div>
        </Shell>
    );
}

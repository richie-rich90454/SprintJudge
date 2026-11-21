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
    const [providers, setProviders] = useState<AuthProvider[]>([]);
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
                            <span className="font-extrabold tracking-tight">SprintJudge Admin</span>
                        </div>
                        <form method="POST" action="/admin/login" className="flex flex-col gap-4">
                            <label className="label-caps block mb-1" htmlFor="al-user">
                                Username
                            </label>
                            <TextInput
                                id="al-user"
                                name="username"
                                placeholder="admin"
                                autoComplete="username"
                                aria-required="true"
                                aria-invalid={false}
                                aria-describedby="al-error"
                            />
                            <label className="label-caps block mb-1" htmlFor="al-pass">
                                Password
                            </label>
                            <TextInput
                                id="al-pass"
                                name="password"
                                type="password"
                                placeholder="password"
                                autoComplete="current-password"
                                aria-required={true}
                                aria-invalid={false}
                                aria-describedby="al-error"
                            />
                            <p
                                id="al-error"
                                role="alert"
                                className="hidden text-[var(--oq-danger)] text-sm"
                            />
                            <Button type="submit" variant="primary" className="w-full">
                                Sign in
                            </Button>
                            <Link to="/" className="btn btn-secondary w-full">
                                Back to player view
                            </Link>
                        </form>
                        {providers.length > 0 && (
                            <div className="border-t border-[var(--oq-border)] px-6 pb-6">
                                <p className="label-caps mb-3">Or sign in with</p>
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

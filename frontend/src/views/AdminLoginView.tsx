import { Link } from "@tanstack/react-router";
import { Card } from "../components/ui/Card";
import { Button } from "../components/ui/Button";
import { Shell } from "../components/Shell";
import { LogoMark } from "../components/LogoMark";

export function AdminLoginView() {
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
                            <input
                                id="al-user"
                                name="username"
                                placeholder="admin"
                                autoComplete="username"
                                aria-required="true"
                                aria-invalid="false"
                                aria-describedby="al-error"
                                className="input-underline"
                            />
                            <label className="label-caps block mb-1" htmlFor="al-pass">
                                Password
                            </label>
                            <input
                                id="al-pass"
                                name="password"
                                type="password"
                                placeholder="password"
                                autoComplete="current-password"
                                aria-required="true"
                                aria-invalid="false"
                                aria-describedby="al-error"
                                className="input-underline"
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
                    </div>
                </Card>
            </div>
        </Shell>
    );
}

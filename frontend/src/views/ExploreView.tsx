import { Link } from "@tanstack/react-router";
import { useQuery } from "@tanstack/react-query";
import { Shell } from "../components/Shell";
import { Card } from "../components/ui/Card";
import { Button } from "../components/ui/Button";
import { EmptyState, Skeleton } from "../components/ui/Primitives";
import { adminApi } from "../services/AdminApiService";

export function ExploreView() {
    const { data, isPending, isError, refetch } = useQuery({
        queryKey: ["public-quizzes"],
        queryFn: () => adminApi.listQuizzes(),
    });

    return (
        <Shell>
            <div className="page-shell py-10 md:py-14 flex flex-col gap-6">
                <div className="max-w-2xl">
                    <p className="label-caps mb-3">Quiz library</p>
                    <h1 className="text-3xl md:text-4xl font-extrabold tracking-tight">
                        Browse quizzes
                    </h1>
                    <p className="text-[var(--oq-ink-soft)] mt-3 leading-relaxed">
                        A peek at what is available. To play anything you still need a
                        game PIN from your host.
                    </p>
                </div>
                {isPending && (
                    <div className="grid gap-4 md:grid-cols-2" aria-label="Loading quizzes">
                        <Skeleton className="h-36" />
                        <Skeleton className="h-36" />
                        <Skeleton className="h-36" />
                    </div>
                )}
                {isError && (
                    <Card className="p-4">
                        <EmptyState
                            title="Could not load the library"
                            hint="Check your connection and try again."
                            action={
                                <Button variant="secondary" onClick={() => refetch()}>
                                    Retry
                                </Button>
                            }
                        />
                    </Card>
                )}
                {data && data.length === 0 && (
                    <Card className="p-4">
                        <EmptyState
                            title="No quizzes yet"
                            hint="Ask your teacher to publish one."
                        />
                    </Card>
                )}
                {data && data.length > 0 && (
                    <div className="grid gap-4 md:grid-cols-2">
                        {data.map((quiz) => (
                            <Card key={quiz.id} className="p-6 flex flex-col gap-2">
                                <h2 className="text-lg font-extrabold">{quiz.title}</h2>
                                {quiz.description && (
                                    <p className="text-sm text-[var(--oq-ink-soft)] leading-relaxed">
                                        {quiz.description}
                                    </p>
                                )}
                                <div className="flex flex-wrap gap-2 mt-3">
                                    <Link
                                        to="/join"
                                        className="btn btn-secondary btn-sm font-bold"
                                    >
                                        Join with PIN
                                    </Link>
                                    <Link to="/admin" className="btn btn-ghost btn-sm">
                                        Open in dashboard
                                    </Link>
                                </div>
                            </Card>
                        ))}
                    </div>
                )}
            </div>
        </Shell>
    );
}

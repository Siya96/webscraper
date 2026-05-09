export function debounce<T extends (...args: any[]) => any>(fn: T, delay: number): T {
    let timer: NodeJS.Timeout;
    return ((...args: any[]) => {
        clearTimeout(timer);
        timer = setTimeout(() => fn(...args), delay);
    }) as T;
}
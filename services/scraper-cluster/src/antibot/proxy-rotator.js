export class ProxyRotator {
    constructor() {
        this.proxies = this.loadProxies();
        this.index   = 0;
        this.failures = new Map();
        this.MAX_FAILURES = 3;
    }

    loadProxies() {
        const raw = process.env.PROXY_LIST || '';
        return raw.split(',').map(p => {
            // Format: protocol://user:pass@host:port
            const match = p.match(/^(\w+):\/\/([^:]+):([^@]+)@([^:]+):(\d+)$/);
            if (!match) return null;
            const [, protocol, username, password, host, port] = match;
            return { protocol, username, password, host, port: parseInt(port) };
        }).filter(Boolean);
    }

    async getNext() {
        if (this.proxies.length === 0) return null;

        // Round-robin with failure skip
        let attempts = 0;
        while (attempts < this.proxies.length) {
            const proxy = this.proxies[this.index % this.proxies.length];
            this.index++;
            attempts++;

            const failures = this.failures.get(proxy.host) || 0;
            if (failures < this.MAX_FAILURES) return proxy;
        }

        // Reset failures if all proxies exhausted
        this.failures.clear();
        return this.proxies[0];
    }

    reportFailure(proxy) {
        const count = (this.failures.get(proxy.host) || 0) + 1;
        this.failures.set(proxy.host, count);
    }
}
import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  trailingSlash: true,
  async rewrites() {
    return [
      {
        source: '/api/:path*',
        destination: 'http://backend:8080/api/:path*', // Proxy to Django
      },
      {
        source: '/metrics',
        destination: 'http://backend:8080/metrics/',
      },
      {
        source: '/metrics/',
        destination: 'http://backend:8080/metrics/',
      },
      {
        source: '/metrics/:path*',
        destination: 'http://backend:8080/metrics/:path*', // Proxy Prometheus metrics to Django
      },
      {
        source: '/v3/:path*',
        destination: 'http://backend:8080/v3/:path*',
      },
      {
        source: '/swagger-ui/:path*',
        destination: 'http://backend:8080/swagger-ui/:path*',
      },
      {
        source: '/webjars/:path*',
        destination: 'http://backend:8080/webjars/:path*',
      },
      {
        source: '/ws/:path*',
        destination: 'http://backend:8080/ws/:path*', // Proxy WebSockets to Django
      },
    ];
  },
};

export default nextConfig;

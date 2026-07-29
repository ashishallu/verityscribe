/** @type {import('next').NextConfig} */
const nextConfig = {
  typescript: {
    ignoreBuildErrors: false,
  },
  images: {
    unoptimized: true,
  },
  turbopack: {
    rules: {
      '*.module.css': {
        loaders: ['style-loader', 'css-loader'],
      },
    },
  },
  onDemandEntries: {
    maxInactiveAge: 15000,
    pagesBufferLength: 5,
  },
}

export default nextConfig

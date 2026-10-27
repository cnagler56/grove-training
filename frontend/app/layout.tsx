import type { Metadata, Viewport } from "next";
import { Nunito } from "next/font/google";
import "./globals.css";
import { Analytics } from "@vercel/analytics/next";
import { DialogProvider } from "@/src/components/Dialogs";
import { BRAND_BLUE } from "@/src/lib/appIcon";

// Rounded, friendly sans for the whole site.
const nunito = Nunito({ subsets: ["latin"], variable: "--font-nunito" });

// Public defaults (home page, /apply). The staff area's layout overrides
// these and hides itself from search engines.
export const metadata: Metadata = {
  title: "Grove Salons",
  description: "Haircuts, color, and styling from a friendly local team at Grove Salons.",
  // Installed on a phone ("Add to Home Screen"), it runs full screen like an app.
  applicationName: "Grove Salons",
  appleWebApp: { capable: true, title: "Grove Salons", statusBarStyle: "default" },
  formatDetection: { telephone: false },
  // Older iPhones only go full screen from the home screen with Apple's original tag.
  other: { "apple-mobile-web-app-capable": "yes" },
};

// Phone status bar and browser chrome match the steel-blue header.
export const viewport: Viewport = {
  themeColor: BRAND_BLUE,
  width: "device-width",
  initialScale: 1,
  viewportFit: "cover",
};

export default function RootLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en" className={nunito.variable}>
      <body>
        <DialogProvider>{children}</DialogProvider>
        {/* Page views for Vercel's Analytics tab. Turned on per project in Vercel; does nothing locally. */}
        <Analytics />
      </body>
    </html>
  );
}

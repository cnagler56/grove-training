import type { Metadata } from "next";
import { Nunito } from "next/font/google";
import "./globals.css";

// Rounded, friendly sans for the whole site.
const nunito = Nunito({ subsets: ["latin"], variable: "--font-nunito" });

export const metadata: Metadata = {
  title: "Grove Salons Training",
  description: "Training videos for the Grove Salons team.",
  // Staff-only site: keep it out of search engines.
  robots: { index: false, follow: false },
};

export default function RootLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en" className={nunito.variable}>
      <body>{children}</body>
    </html>
  );
}

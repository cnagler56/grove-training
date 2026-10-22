import type { Metadata } from "next";
import { Nunito } from "next/font/google";
import "./globals.css";
import { DialogProvider } from "@/src/components/Dialogs";

// Rounded, friendly sans for the whole site.
const nunito = Nunito({ subsets: ["latin"], variable: "--font-nunito" });

// Public defaults (home page, /apply). The staff area's layout overrides
// these and hides itself from search engines.
export const metadata: Metadata = {
  title: "Grove Salons",
  description: "Haircuts, color, and styling from a friendly local team at Grove Salons.",
};

export default function RootLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en" className={nunito.variable}>
      <body>
        <DialogProvider>{children}</DialogProvider>
      </body>
    </html>
  );
}

import { Inter, JetBrains_Mono } from "next/font/google";
import "./globals.css";

const inter = Inter({
  variable: "--font-inter",
  subsets: ["latin"],
});

const jetbrainsMono = JetBrains_Mono({
  variable: "--font-mono",
  subsets: ["latin"],
});

export const metadata = {
  title: "GyroCursor // Kinetic Command Center",
  description: "Next-gen industrial HUD for gyroscope cursor control. Seamless latency. Zero lag.",
};

export default function RootLayout({ children }) {
  return (
    <html
      lang="en"
      className={`${inter.variable} ${jetbrainsMono.variable} h-full antialiased`}
    >
      <body className="min-h-full bg-[#131313] text-[#e2e2e2] antialiased select-none">
        <div className="scanlines" />
        {children}
      </body>
    </html>
  );
}

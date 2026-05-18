---
name: Cyber-Industrial Precision
colors:
  surface: '#131313'
  surface-dim: '#131313'
  surface-bright: '#393939'
  surface-container-lowest: '#0e0e0e'
  surface-container-low: '#1b1b1b'
  surface-container: '#1f1f1f'
  surface-container-high: '#2a2a2a'
  surface-container-highest: '#353535'
  on-surface: '#e2e2e2'
  on-surface-variant: '#baccb0'
  inverse-surface: '#e2e2e2'
  inverse-on-surface: '#303030'
  outline: '#85967c'
  outline-variant: '#3c4b35'
  surface-tint: '#2ae500'
  primary: '#efffe3'
  on-primary: '#053900'
  primary-container: '#39ff14'
  on-primary-container: '#107100'
  inverse-primary: '#106e00'
  secondary: '#c8c6c5'
  on-secondary: '#313030'
  secondary-container: '#4a4949'
  on-secondary-container: '#bab8b7'
  tertiary: '#fdf9f9'
  on-tertiary: '#313030'
  tertiary-container: '#e0dddc'
  on-tertiary-container: '#626161'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#79ff5b'
  primary-fixed-dim: '#2ae500'
  on-primary-fixed: '#022100'
  on-primary-fixed-variant: '#095300'
  secondary-fixed: '#e5e2e1'
  secondary-fixed-dim: '#c8c6c5'
  on-secondary-fixed: '#1c1b1b'
  on-secondary-fixed-variant: '#474646'
  tertiary-fixed: '#e5e2e1'
  tertiary-fixed-dim: '#c9c6c5'
  on-tertiary-fixed: '#1c1b1b'
  on-tertiary-fixed-variant: '#474646'
  background: '#131313'
  on-background: '#e2e2e2'
  surface-variant: '#353535'
typography:
  headline-lg:
    fontFamily: Inter
    fontSize: 48px
    fontWeight: '800'
    lineHeight: '1.1'
    letterSpacing: -0.04em
  headline-md:
    fontFamily: Inter
    fontSize: 32px
    fontWeight: '700'
    lineHeight: '1.2'
    letterSpacing: -0.02em
  headline-sm:
    fontFamily: Inter
    fontSize: 24px
    fontWeight: '600'
    lineHeight: '1.3'
    letterSpacing: 0em
  body-lg:
    fontFamily: JetBrains Mono
    fontSize: 16px
    fontWeight: '400'
    lineHeight: '1.6'
    letterSpacing: 0em
  body-md:
    fontFamily: JetBrains Mono
    fontSize: 14px
    fontWeight: '400'
    lineHeight: '1.5'
    letterSpacing: 0em
  label-caps:
    fontFamily: JetBrains Mono
    fontSize: 12px
    fontWeight: '700'
    lineHeight: '1'
    letterSpacing: 0.1em
  telemetry-data:
    fontFamily: JetBrains Mono
    fontSize: 18px
    fontWeight: '500'
    lineHeight: '1'
    letterSpacing: -0.02em
rounded:
  sm: 0.125rem
  DEFAULT: 0.25rem
  md: 0.375rem
  lg: 0.5rem
  xl: 0.75rem
  full: 9999px
spacing:
  unit: 4px
  gutter: 16px
  margin-mobile: 16px
  margin-desktop: 48px
  panel-gap: 24px
---

## Brand & Style

This design system is built on the principle of **Cyber-Industrial Precision**. It targets a user base that demands technical accuracy, high-performance feedback, and a futuristic, command-center aesthetic. The visual language merges the raw utility of industrial interfaces with the sleek, high-contrast energy of near-future technology.

The UI should evoke a sense of controlled power and absolute clarity. The style utilizes **Glassmorphism** to create depth through high-refraction layers, balanced against **Minimalism** to ensure data density remains readable. Visual interest is driven by "Scanning" lines and CRT-style overlays, suggesting a live, data-driven environment.

## Colors

The palette is anchored in **Absolute Black (#000000)** to maximize contrast and eliminate visual noise. The primary accent is **Neon Toxic Green (#39FF14)**, used exclusively for critical data points, active states, and interactive triggers to draw immediate ocular attention.

Dark Grays (#0A0A0A and #121212) are used for structural layering and surface definition. These grays provide the necessary separation for glassmorphic containers without breaking the deep-space immersion of the black background. All colors should be applied with high saturation in mind to facilitate the "bloom" effect on interactive elements.

## Typography

The typography strategy leverages two distinct voices. **Inter** is used for high-level headlines and navigation, providing a grounded, professional feel that balances the more aggressive technical elements.

**JetBrains Mono** is the workhorse of the design system, used for all body text, labels, and telemetry data. Its monospaced nature reinforces the industrial/coding aesthetic and ensures that numerical data remains perfectly aligned across shifting values. For mobile devices, headlines should scale down by 20% while maintaining the tight letter spacing to preserve the "high-density" look.

## Layout & Spacing

This design system follows a **Fixed-Fluid Hybrid Grid**. Content is housed within structured panels that align to a 12-column grid on desktop, shifting to a single-column stack on mobile. 

The layout philosophy mimics a **Head-Up Display (HUD)**. Key telemetry and navigation occupy the edges of the viewport (safe areas), while the center remains open for primary interaction. Spacing is governed by a 4px base unit, ensuring all elements feel mechanically precise. Use wide margins (48px+) on desktop to allow the background absolute black to provide "breathing room" for the neon accents.

## Elevation & Depth

Depth is achieved through **Glassmorphism** rather than traditional drop shadows.
- **Surface Layers:** Use a background blur of 40px to 60px on containers.
- **Borders:** Containers must feature ultra-thin 0.5px borders using a semi-transparent Toxic Green or high-contrast White (10-20% opacity).
- **Overlays:** A global "Scanline" overlay (low-opacity horizontal lines) should be applied to the background to simulate a CRT monitor.
- **Interactive Bloom:** Hovered or active elements do not just change color; they emit a "glow" (outer glow) using the primary Toxic Green, creating a blooming effect that suggests high energy.

## Shapes

The shape language is strictly **Soft (Level 1)**. This allows for a microscopic 0.25rem (4px) radius on containers and buttons, just enough to prevent the UI from feeling dangerously sharp while maintaining a rigid, industrial silhouette. Large components like cards or "TelemetryPanels" should use 0.5rem for a subtle distinction. Avoid full pills or circles unless used for status indicators.

## Components

### TelemetryPanel
The primary container for data. Features a 0.5px border, 40px background blur, and a subtle "scanning" animation—a horizontal line that sweeps vertically across the panel every 5 seconds at 5% opacity.

### InteractiveQRCard
A specialized card for system handoffs. The QR code should be rendered in Toxic Green on a dark gray (#121212) base. On hover, the card scales up by 2% and the border intensity increases to 100% opacity with a blooming glow.

### MotionTrackHeader
A sticky header that displays real-time coordinates or system status in JetBrains Mono. It should feel lightweight, using only a bottom 0.5px border and a semi-transparent background to allow content to blur beneath it as it scrolls.

### Buttons & Inputs
- **Primary Button:** Ghost style with a 1px Toxic Green border. On hover, fills with Toxic Green (text turns Black) and triggers a 10px outer glow bloom.
- **Input Fields:** Bottom-border only (0.5px). Placeholder text in JetBrains Mono at 40% opacity. Active state illuminates the bottom border to full Toxic Green.
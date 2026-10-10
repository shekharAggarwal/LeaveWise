// Custom Text types defined in leavewise.source.ts (`components.text`). `astryx theme build` 0.6.6 generates the
// Heading and Button augmentations but not these, so they are declared here, as core's theme/types.d.ts describes.
export {};

declare module '@astryxdesign/core/theme' {
  interface CustomTextTypes {
    wordmark: true;
    field: true;
    small: true;
    figure: true;
    ratio: true;
    lead: true;
  }
}

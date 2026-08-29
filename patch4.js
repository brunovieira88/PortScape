const fs = require('fs');
let code = fs.readFileSync('frontend/src/scene/StreetLayout.tsx', 'utf8');

code = code.replace(
  "export function StreetLayout({ scanData, offsetX, offsetZ, gridWidth, gridDepth }: { scanData: any, offsetX: number, offsetZ: number, gridWidth: number, gridDepth: number }) {",
  "export function StreetLayout({ offsetX, offsetZ, gridWidth, gridDepth }: { offsetX: number, offsetZ: number, gridWidth: number, gridDepth: number }) {"
);

fs.writeFileSync('frontend/src/scene/StreetLayout.tsx', code);

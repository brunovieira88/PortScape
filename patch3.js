const fs = require('fs');
let code = fs.readFileSync('frontend/src/ui/DeviceListPanel.tsx', 'utf8');

// Replace the component signature
code = code.replace(
  "export function DeviceListPanel({ scanData, onSelectHost, onOpenDetails }: { scanData: any, onSelectHost: (host: any) => void, onOpenDetails?: (host: any) => void }) {",
  "export function DeviceListPanel({ scanData, onOpenDetails }: { scanData: any, onOpenDetails?: (host: any) => void }) {"
);

fs.writeFileSync('frontend/src/ui/DeviceListPanel.tsx', code);

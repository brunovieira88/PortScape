const fs = require('fs');
let code = fs.readFileSync('frontend/src/scene/StreetLayout.tsx', 'utf8');

// Remove the import
code = code.replace("import { CyberBillboard } from './CyberBillboard';\n", '');

// Remove the billboard spawning block:
/*
        // Outdoors Cyberpunk (Billboards) aleatórios nos passeios
        if (Math.random() > 0.85) {
          // Virado para a estrada (aleatório X ou Z)
          const rot = Math.random() > 0.5 ? 0 : Math.PI / 2;
          items.push(<CyberBillboard key={`billboard-${kx}-${kz}`} x={cornerX - 2} z={cornerZ} rotationY={rot} />);
        }
*/
const toRemove = `
        // Outdoors Cyberpunk (Billboards) aleatórios nos passeios
        if (Math.random() > 0.85) {
          // Virado para a estrada (aleatório X ou Z)
          const rot = Math.random() > 0.5 ? 0 : Math.PI / 2;
          items.push(<CyberBillboard key={\`billboard-\${kx}-\${kz}\`} x={cornerX - 2} z={cornerZ} rotationY={rot} />);
        }`;

code = code.replace(toRemove, '');
fs.writeFileSync('frontend/src/scene/StreetLayout.tsx', code);

const fs = require('fs');
let code = fs.readFileSync('frontend/src/scene/StreetLayout.tsx', 'utf8');

// Remove import
code = code.replace("import { ZebraCrossing } from './ZebraCrossing';\n", '');

// Remove zebra block:
const toRemove = `        // C) Cruzamentos e Passadeiras (Zebra Crossings)
        if (kx < endKx && kz < endKz) {
          const centerX = realX + SCALE / 2;
          const centerZ = realZ + SCALE / 2;
          // Offset de 6 unidades a partir do centro do cruzamento coloca a passadeira exatamente no fim do passeio
          const offset = 6; 
          
          // Passadeiras que atravessam a rua principal Z (as linhas deitam-se no eixo X)
          items.push(<ZebraCrossing key={\`zebra-n-\${kx}-\${kz}\`} x={centerX} z={centerZ - offset} isVertical={false} />);
          items.push(<ZebraCrossing key={\`zebra-s-\${kx}-\${kz}\`} x={centerX} z={centerZ + offset} isVertical={false} />);
          
          // Passadeiras que atravessam a rua transversal X (as linhas deitam-se no eixo Z)
          items.push(<ZebraCrossing key={\`zebra-w-\${kx}-\${kz}\`} x={centerX - offset} z={centerZ} isVertical={true} />);
          items.push(<ZebraCrossing key={\`zebra-e-\${kx}-\${kz}\`} x={centerX + offset} z={centerZ} isVertical={true} />);
        }

`;

code = code.replace(toRemove, '');
fs.writeFileSync('frontend/src/scene/StreetLayout.tsx', code);

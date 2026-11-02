# Real Power - Arquitetura

Minecraft 1.21.1, NeoForge 21.1.x, Java 21.

## Modulos

| Modulo | Mod id | Funcao |
|---|---|---|
| `core` | `realpower` | Energia, multiblocos em escala real, API publica, controle e dashboards |
| `quarry` | `realpower_quarry` | Addon: mineracao/quarry realista (depende do core) |
| `compat-*` (futuro) | `realpower_compat_*` | Um modulo por mod de terceiros |

## Comandos

```
gradlew build                       # compila tudo
gradlew :core:runClient             # abre o Minecraft com o core
gradlew :quarry:runClient           # abre o Minecraft com core + quarry
```

## Decisao de design: escala real (1 bloco = 1 metro)

Estruturas reais sao enormes (represa com 100 m+ de altura, turbina eolica com
80 a 150 m, reator com dezenas de metros). Isso muda a abordagem classica de multibloco:

1. **Sem padrao fixo (template).** A deteccao e *parametrica*: o jogador constroi
   livremente e o controlador valida regras (dimensoes minimas, material, volume de
   agua, altura de queda, vao entre as pas...), em vez de comparar com uma planta.
2. **Validacao incremental e assincrona.** Escanear milhares de blocos de uma vez
   trava o tick. A varredura roda em fatias por tick (ou coleta o snapshot no tick e
   processa fora dele) e fica em cache ate um bloco da estrutura mudar.
3. **Estruturas cruzam chunks.** O controlador deve tolerar chunks descarregados e
   usar chunk tickets so enquanto necessario.
4. **Saida baseada em fisica simplificada, nao em contagem de blocos.** Ex.: represa
   `P = rho * g * Q * h * eficiencia`; eolica `P ~ 0,5 * rho * A * v^3 * Cp`.
5. **Renderizacao.** Nao renderizar nada pesado por bloco. Partes moveis (pas de
   turbina) via um unico BlockEntityRenderer no controlador.
6. **Energia.** Usar a capability `IEnergyStorage` (compat com outros mods). Para
   valores de fusao/fissao, manter `long` internamente e converter na borda.

## Tiers (`api/tier/Tier`)

| Tier | Eficiencia | Transferencia max. (FE/tick) |
|---|---|---|
| 1 Basic | 0,50 | 512 |
| 2 Industrial | 0,68 | 4.096 |
| 3 High Voltage | 0,80 | 65.536 |
| 4 Advanced | 0,90 | 1.048.576 |
| 5 Future | 0,97 | 16.777.216 |

Blocos e block entities de um tier implementam `Tiered`. Hoje o controlador da
turbina existe nos tiers Basic e Industrial (o Industrial exige aco).

## Geradores (`generator/AbstractGeneratorBlockEntity`)

Base de todo gerador multibloco: validacao incremental, producao, buffer e envio de FE.
Um novo gerador implementa apenas `createScan`, `onStructureScanned`,
`currentOutputWatts` e `ratedOutputWatts`.

- Saida em FE; 1 FE/tick = 1 kW (`PowerScale`).
- O buffer (`GeneratorBuffer`) e `long` e guarda de 5 s (eficiencia 0) a 20 s
  (eficiencia 1) da potencia nominal, entao tiers maiores guardam mais.
- Para outros mods ele e exposto como `IEnergyStorage` (so extracao, limitado a int).

## Aparencia: andaime que vira modelo 3D

O jogador constroi um **andaime** de blocos (`wind_tower`, `wind_blade`, controlador). Quando
o controlador reconhece a estrutura, os blocos recebem `formed=true`: ficam invisiveis (as pas
tambem perdem colisao) e o `WindTurbineRenderer` desenha no lugar um modelo 3D procedural.

- `render/TurbineModel` gera a malha em escala real a partir da altura da torre e do comprimento
  da pa: torre conica, nacele, cone do cubo e 3 pas com corda, espessura e torcao variaveis, ponta
  vermelha de sinalizacao. E Java puro (sem Minecraft), entao da para gerar previas fora do jogo.
- O rotor gira conforme o vento (razao de velocidade de ponta 7), sem recalcular a malha.
- O estado e sincronizado ao cliente por `getUpdatePacket`. A geometria "morfada" fica salva no
  NBT para desfazer corretamente ao quebrar o controlador ou a estrutura.
- Previas geradas por rasterizador de software em `docs/preview/`.

## Overlay fantasma do andaime

Segurando o controlador, a torre ou a pa, `client/ScaffoldGhostRenderer` desenha o andaime minimo
(`WindTurbineScaffold.minimum`, o mesmo que o validador aceita, coberto por GameTest):
mirando um bloco mostra onde ficaria a turbina; mirando um controlador ja colocado mostra so o que
falta. Azul = torre, branco = pa e cubo, laranja = controlador, vermelho = posicao bloqueada.

## Unificacao de materiais

As receitas usam tags convencionais `c:` (`c:ingots/iron`, `c:ingots/copper`, `c:ingots/steel`...),
que e o padrao do NeoForge. Assim uma biblioteca de unificacao de minerios pode ser adicionada
depois sem reescrever receitas. O aco do mod e registrado em `c:ingots/steel`.

## Proximos passos sugeridos

1. API de multibloco (`api/multiblock`): controlador, regras, resultado de validacao.
2. Primeiro gerador: turbina eolica (mais simples) para validar o sistema.
3. Sistema de controle: bloco de painel + `Menu`/`Screen` com sincronizacao por payload.
4. Represa, depois fissao, depois fusao.

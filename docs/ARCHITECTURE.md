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

## Turbina eolica: modelo padrao em escala real

A turbina e uma planta fixa (`multiblock/wind/TurbineBlueprint`, ~4900 blocos) orientada pelo
controlador. O controlador fica no topo da laje, dentro da torre; o rotor aponta para onde o
jogador olhava ao colocar e a porta fica atras (u negativo).

| Parte | Bloco | Medidas |
|---|---|---|
| Base | `turbine_base` | laje 21x21x3 (2 camadas abaixo do controlador) |
| Corpo | `turbine_casing` | 80 m, casca oca de raio 5,5 a 3,5 m, porta 3x3 |
| Nacele | `turbine_casing` | 15x9x7, escotilha no piso sobre o interior da torre |
| Carenagem | `rotor_fairing` | elipsoide oco, raio 3,8 m |
| Helices | `turbine_blade` | 3 pas de 47 m com corda variavel |

O interior da torre e vazio: o jogador entra pela porta, sobe (escada/elevador) e chega a
nacele pela escotilha. `WindTurbineScan` valida incrementalmente (256 blocos/tick) e conta o que
falta por tipo. Ao formar, carenagem e helices recebem `formed=true` (invisiveis, sem colisao) e o
`WindTurbineRenderer` desenha o rotor 3D giratorio (`render/TurbineModel`, Java puro).

Detalhes do modelo formado: corpo e nacele tambem viram malha 3D (`TurbineModel.buildStructure`). O
`turbine_casing` formado fica invisivel e opaco para a luz; sua colisao segue a parede lisa via
`TurbineCasingBlock.MASK` (9 sub-colunas 3x3 por bloco, calculadas no blueprint). O interior usa a luz
real do mundo por faixas de 10 m, entao precisa de `industrial_light` (componente comum a todos os
multiblocos). As luzes vermelhas de posicao (teto da nacele e ponta das pas) so existem no modelo.

## Overlay de construcao

Cada controlador colocado desenha o projeto faltante (`client/ScaffoldGhostRenderer`) ate a
estrutura formar. Liga/desliga na tela do controlador (`TurbineMenu`/`TurbineScreen`, provisoria).
Cinza = base, azul = corpo/nacele, ciano = carenagem, branco = helice, vermelho = posicao
ocupada por outro bloco. So desenha blocos a ate 72 m da camera (max. 2500 caixas).

## Unificacao de materiais

As receitas usam tags convencionais `c:` (`c:ingots/iron`, `c:ingots/copper`, `c:ingots/steel`...),
que e o padrao do NeoForge. Assim uma biblioteca de unificacao de minerios pode ser adicionada
depois sem reescrever receitas. O aco do mod e registrado em `c:ingots/steel`.

## Cabos (`cable/`)

- `CableBlock` (tiers BASIC/INDUSTRIAL) conecta nas faces cujo vizinho expoe `Capabilities.EnergyStorage.BLOCK`; o estado das conexoes e recalculado em `neighborChanged` e em um tick agendado apos `onPlace` (trocar o estado dentro de `onPlace` impede o aviso aos vizinhos).
- `CableNetworks` (por mundo) registra os `CableBlockEntity` carregados e refaz as redes por busca em largura quando algo muda. `CableNetwork` e um pool: geradores empurram energia pela capability do cabo (so recebe), e a cada tick a rede entrega aos consumidores encostados nela.
- Vazao = a do cabo mais fraco da rede (`Tier.maxTransferPerTick`); buffer = 2 ticks de vazao. Perda = `Tier.lineLossPerBlock` x vao da rede (soma das 3 dimensoes da caixa que a contem), limitada a 50%.
- Nos (`CableKind`): cabo, rele, poste e torre, cada um nos 5 tiers (postes ate alta tensao, torres a partir dela). Cabos e reles sao enterrados.
- Poste (12 m) e torre (36 m) sao multiblocos em escala real: o bloco base (`CableBlock`, com `FACING` = direcao das travessas) preenche acima de si uma coluna de `PowerStructureBlock` (invisivel; poste com colisao fina, torre sem colisao); quebrar qualquer parte derruba o conjunto. A estrutura 3D (trelica afunilada, 3 travessas cada vez mais largas, cadeias de isoladores) e desenhada por `PowerLineRenderer` a partir de `render/PowerStructureModel`, e os fios saem das ancoras rotacionadas.
- Ligacao aerea: grafo de vizinhanca relativa (`CableNetworks.computeLinks`) - dois nos so se ligam se nenhum terceiro alcancavel estiver mais perto de ambos, entao a linha nunca atravessa um vizinho. Alcance: poste 48, torre 160.
- Perda: comprimento equivalente = soma das corridas de cabo (vao da corrida / (1 + reles)) + linhas aereas (comprimento x fator: poste 0,25, torre 0,08). Reles e linhas aereas mitigam a perda por distancia.
- Turbina: a porta de energia e um bloco unico (`TurbinePowerPortBlock`) sobre a laje, em qualquer um dos 4 cantos; recebe a energia do controlador (`extraOutput`) e a passa aos cabos encostados.
- Clique direito num cabo mostra tier, cabos, vazao, perda e buffer. Testes: `CableGameTests` (consumidor de teste `TestEnergySink` so existe com gametest ativo).

## Proximos passos sugeridos

1. API de multibloco (`api/multiblock`): controlador, regras, resultado de validacao.
2. Primeiro gerador: turbina eolica (mais simples) para validar o sistema.
3. Sistema de controle: bloco de painel + `Menu`/`Screen` com sincronizacao por payload.
4. Represa, depois fissao, depois fusao.

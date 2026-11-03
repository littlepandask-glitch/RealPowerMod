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
- Transformador elevador (`CableKind.TRANSFORMER`, a partir do tier industrial): multibloco que cresce com o tier (`CableKind.size`: 3x3x3, 5x3x4, 7x5x5, 9x5x6 m), preenchido por `TRANSFORMER_STRUCTURE` e desenhado por `PowerStructureModel.transformer` (tanque, radiadores, conservador, 3 buchas). Regra: multiblocos ligaveis por cabos comuns tem portas de energia visiveis; aqui sao 4 celulas `TRANSFORMER_PORT` ao nivel do chao, uma no centro de cada face lateral (capability via `CableBlock.baseOfPort`, mescladas na rede do transformador em `CableNetworks.rebuild`). Liga-se por fio (alcance 48) a postes e torres.
- Ligacao aerea: postes e torres so ligam ao longo do eixo das travessas (ate 30 graus; `CableNetworks.alongAxis`), nunca de lado. Entre os que podem, grafo de vizinhanca relativa (`CableNetworks.computeLinks`) - dois nos so se ligam se nenhum terceiro alcancavel estiver mais perto de ambos, entao a linha nunca atravessa um vizinho. Alcance: poste 48, torre 160.
- Perda: comprimento equivalente = soma das corridas de cabo (vao da corrida / (1 + reles)) + linhas aereas (comprimento x fator: poste 0,25, torre 0,08). Reles e linhas aereas mitigam a perda por distancia.
- Turbina: a porta de energia e um bloco unico (`TurbinePowerPortBlock`) sobre a laje, em qualquer um dos 4 cantos; recebe a energia do controlador (`extraOutput`) e a passa aos cabos encostados.
- Oculos de engenheiro (`goggles/`, `EngineerGogglesItem`): equipados na cabeca ou no slot `head` do Curios (reflexao, tag `curios:head`), o servidor descreve o bloco observado a cada 5 ticks (`Goggles.describe`) e envia `GogglesPayload`; `client/GogglesHud` desenha o balao no canto superior esquerdo. Chave inglesa (`WrenchItem`): shift + clique direito remove blocos do mod; sem shift reservado a configuracoes futuras. Testes: `CableGameTests` (consumidor de teste `TestEnergySink` so existe com gametest ativo).

## Rede de dados e gerenciamento (`data/`, `computer/`, `screen/`)

- Rede de dados separada da de energia, sem estado global: `DataNetwork.scan` faz busca em largura (max 1024 nos) a partir das portas do computador sobre blocos `DataNode` (`DataCableBlock` fino, `DataPortBlock`) e encontra equipamentos pelas portas, que expoem o `DataDevice` do bloco encostado (`DataPortBlock.deviceOf`, que resolve multiblocos via `StructureBase`/`findBase`). Na turbina a porta de dados encosta numa porta de energia ou no controlador.
- `DataDevice`: telemetria (online, potencia, armazenado, capacidade), identidade (varias portas do mesmo equipamento contam uma vez) e controle (`enabled`, `limit` em %). `AbstractGeneratorBlockEntity` implementa; producao = `fePerTick * limit / 100` quando ligado.
- Computador (`ComputerBlock`, rack 3x1x2, base embaixo no centro, portas de dados nas duas pontas do chao; basico 8 equipamentos, x4 por tier): a cada segundo varre a rede e alimenta `Metric` (aneis de 300 amostras de 1 s + 288 medias de 5 min, salvos em NBT). Regras (`ControlRule`, ate 8; "se geracao/consumo (kW) ou armazenado (%) acima/abaixo de X, ligar/desligar/limitar/redstone") sao avaliadas na ordem e a ultima verdadeira vence; para histerese use duas regras. Redstone sai do bloco base (15). Menu: `ComputerMenu`/`ComputerScreen` (botoes ciclicos por campo via `clickMenuButton`).
- Telas (`ScreenBlock`): blocos coplanares formam retangulo ate 8x6 (`ScreenGroup`, senao 1x1); o mestre (canto superior esquerdo) desenha tudo (`ScreenRenderer`) e trata cliques (`ScreenLayout` e a geometria compartilhada servidor/cliente: abas Geral/Geradores/Graficos/Alarmes, janela 5 min/24 h, liga/desliga e limite por gerador). O servidor envia `ScreenPayload` a jogadores a 48 blocos a cada segundo; o cliente guarda em `ScreenData`.
- Oculos mostram porta de dados, computador, tela e estado de controle; a chave inglesa sem shift liga/desliga o equipamento. Testes: `DataNetworkGameTests`, `ScreenGameTests`, `WindTurbineGameTests`.

## Banco de capacitores (`storage/`)

- `CapacitorBankBlock` (um por tier, `StructureBase`): galpao que cresce com o tier (`size(tier)`: 3x3x2 basico ate 9x5x4 futuro), base no centro da face inferior; celulas `CAPACITOR_STRUCTURE` (preenchimento) e 4 `CAPACITOR_PORT` no chao, uma no centro de cada face lateral (regra de portas visiveis; capability de energia registrada em `RealPower.registerCapabilities`, resolvendo a base por `findBase`). Capacidade = volume x `density(tier)`.
- `CapacitorBankBlockEntity`: guarda `long` em `GeneratorBuffer`; a visao `IEnergyStorage` e um `BufferStorage` (marcador). Vazao por tick = `tier.maxTransferPerTick x limite%`, contada por tick de jogo; desligado, nao troca energia. E `DataDevice` controlavel (sem potencia propria: o computador soma o armazenado e deduz o consumo pela variacao).
- `CableNetwork.tick`: endpoints `BufferStorage` sao reservas, os demais consumidores. Ordem: reservas cobrem o que falta para a demanda (simulada nos consumidores), consumidores recebem, e o que sobrar em `stored` carrega as reservas. Perda de transmissao so na entrega aos consumidores.
- `CapacitorBankRenderer` + `PowerStructureModel.capacitorBank`: gabinete com nervuras, portas com conector luminoso e barras de LED na frente (passos de 5% da carga, sincronizada por `getUpdateTag`). Testes: `CapacitorGameTests`.

## Proximos passos sugeridos

1. API de multibloco (`api/multiblock`): controlador, regras, resultado de validacao.
2. Primeiro gerador: turbina eolica (mais simples) para validar o sistema.
3. Sistema de controle: bloco de painel + `Menu`/`Screen` com sincronizacao por payload.
4. Fissao, depois fusao (a represa ja existe).

## Estilo industrial e tela local (`render/IndustrialStyle`, `screen/`)

- `IndustrialStyle` e o estilo dos blocos funcionais novos (transformador, banco de capacitores; usar nos proximos): chapas cinza por tier (`Palette`: chapa + brilho), cantos luminosos (`node`), painel com raio (`bolt`), barras de LED (`ledPad`), cilindros. Quads "brilhantes" (`Mesh.Builder.glow`) ignoram a luz ambiente.
- Tela local: sem computador na rede de dados, o mestre do painel usa o equipamento (ou multibloco) encostado em qualquer bloco do painel (`ScreenBlockEntity.refreshLocal`), com historico em memoria; liga/desliga e limite funcionam na aba de equipamentos.

## Represa (`multiblock/hydro/`)

- Fisica (`HydroPhysics`): `P = rho g Q H eta`. Vazao `Q = min(Q_nominal(tier), 0,02 m3/s x area do lago)`, queda util entre `minHead` e `maxHead` do tier (abaixo do minimo nao gera). Industrial 25 m3/s / 40 m (~3 MW), Alta tensao 120 / 80 (~50 MW), Avancado 700 / 120 (~500 MW).
- `DamBlock` (`dam_concrete`, `dam_crest`): modulos de 1 m que o jogador empilha para fechar o vale. Sem logica propria; sao so lidos por `DamScan`.
- `DamScan` (incremental, 256 blocos/tick): mede o nivel do lago a partir da tomada d'agua, confere linha a linha (do chao da unidade ate a superficie) que a parede de modulos/da propria unidade vai de margem a margem terminando em terreno solido, e conta a superficie do lago (BFS 2D, teto 50 mil). Problemas voltam como `ValidationResult` (mostrados nos oculos).
- `HydroUnitBlock` (um por tier: Industrial 7x9x9, Alta tensao 11x14x13, Avancado 15x20x19; largura x altura x profundidade): `StructureBase` com `FACING` = sentido do canal de fuga (jusante); o lago fica atras (`upstream`), a tomada d'agua encosta na parede. Preenchimento `HYDRO_STRUCTURE` (invisivel, flags baratas) e 2 `HYDRO_PORT` no chao dos cantos de jusante (visiveis, capability de energia, empurrando para cabos encostados). `containsCell` por aritmetica evita montar a lista de celulas; `PowerStructureBlock.findBase` tem cache LRU e alcance 10.
- `HydroUnitBlockEntity extends AbstractGeneratorBlockEntity`: a comporta e o `DataDevice` (liga/desliga e limite %, por computador, telas, chave inglesa). Potencia = `HydroPhysics.watts(tier, lago - eixo da turbina, area)`.
- Visual: `HydroModel` (concreto realista, fora do estilo industrial; salao, tubo forcado, caixa espiral, grade da tomada, ponte rolante) + `HydroUnitRenderer`; o rotor e uma malha separada que gira conforme `runFraction()`.
- Terraformador (`TerraformerItem`, `TerraformJob`, `TerraformOverlay`): clique em dois blocos marca a caixa (ate 64 por lado, guardada em `CUSTOM_DATA`); clique no ar executa, agachado + clique no ar alterna escavar/aterrar. Tarefa de 1000 blocos/tick, sem drops, preserva maquinas e blocos indestrutiveis; aterro consome pedra/pedregulho/terra e a ferramenta desgasta 1 a cada 64 blocos. Testes: `HydroGameTests`.

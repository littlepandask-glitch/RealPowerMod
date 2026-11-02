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

## Proximos passos sugeridos

1. API de multibloco (`api/multiblock`): controlador, regras, resultado de validacao.
2. Primeiro gerador: turbina eolica (mais simples) para validar o sistema.
3. Sistema de controle: bloco de painel + `Menu`/`Screen` com sincronizacao por payload.
4. Represa, depois fissao, depois fusao.

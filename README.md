# Attributes Viewer (Minecraft 1.18.2 / Forge)

Tela de atributos no inventario, igual a do Apothic Attributes. Somente visualizacao.

## Como usar no jogo
1. Abra o inventario (E).
2. Clique no botao com a espada, no alto da area do personagem.
3. Passe o mouse sobre um atributo para ver descricao, valor atual, base, minimo/maximo e o que esta modificando.
4. Ative F3+H para ver tambem o ID do atributo e os valores internos dos modificadores.
5. O quadradinho no rodape do painel liga/desliga "ocultar inalterados".

Por ser mod somente de cliente, funciona em servidores que nao tem ele instalado.

## Como compilar

### Opcao A: GitHub Actions (nao precisa instalar nada)
1. Crie um repositorio no GitHub e envie o conteudo desta pasta.
2. Abra a aba **Actions**, rode o workflow **build** (ou ele roda sozinho no push).
3. Baixe o artefato **AttributesViewer-1.18.2**, extraia e use o `.jar` (sem `-sources`).

### Opcao B: no seu computador
1. Instale o **Java 17**.
2. Dentro desta pasta: `./gradlew build` (no Windows: `gradlew.bat build`).
3. O `.jar` fica em `build/libs/`.

## Instalar no TLauncher
Coloque o `.jar` na pasta `mods` da instancia do Forge 1.18.2 (ex.: `.minecraft/mods`).

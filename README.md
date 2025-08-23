# **TVPlayer \- O seu Player de Listas M3U para Android\!**

## **📖 E aí, qual é a desse projeto?**

Sabe aquelas listas de canais M3U? Então, o **TVPlayer** é um app que eu criei em Kotlin pra gente conseguir carregar e assistir a essas listas de um jeito fácil e sem dor de cabeça\! Você pode simplesmente colar um link da web ou pegar um arquivo que já tenha no celular.

A ideia foi fazer um app com uma arquitetura moderna e limpinha, pensando na performance e em como facilitar a vida na hora de dar manutenção. Ele também já está preparado para as novidades do Android 15\!

## **✨ O que é que ele faz de bom?**

* **Carregue listas de qualquer lugar:** Pode usar um link da internet ou um arquivo .m3u que já tenha guardado. Super flexível\!  
* **Ele entende as listas:** O app tem um parser inteligente que consegue ler e separar direitinho o nome, o logo, o grupo e o link de cada canal.  
* **Tudo organizado:** Chega de listas gigantes e confusas\! Ele agrupa os canais automaticamente em abas como "Canais", "Filmes", "Séries", etc. Fica bem mais fácil de achar o que você quer.  
* **Busca na hora:** É só começar a digitar o nome do canal e a lista vai se filtrando em tempo real.  
* **Guarda a sua última lista:** Pra não ter que ficar inserindo o link toda hora, o app lembra da última lista que você usou.  
* **Um Player que é um show à parte (com motor VLC\!):**  
  * Roda praticamente qualquer formato de vídeo que você imaginar.  
  * **Controles por gestos, que nem os apps famosos:** Deslize o dedo na esquerda da tela para ajustar o brilho e na direita para o volume. É muito intuitivo\!  
  * A interface é super limpa e os controles somem sozinhos para não atrapalhar.  
  * **NOVO\! Picture-in-Picture (PiP):** Quer responder a uma mensagem? Sem problemas\! O vídeo continua tocando numa janelinha enquanto você usa outros apps.  
  * **NOVO\! Botão pra girar a tela:** Quer forçar o vídeo a ficar na horizontal? É só tocar no botão\!  
  * **NOVO\! Transmita para a TV:** Tem um Chromecast? É só tocar no botão e mandar o vídeo pra tela grande\!

## **🛠️ O que tem "debaixo do capô"?**

Pra quem curte a parte técnica, o projeto foi feito com:

* **Linguagem:** 100% [Kotlin](https://kotlinlang.org/), claro\!  
* **Arquitetura:** **MVVM (Model-View-ViewModel)**, pra deixar o código bem organizado e fácil de testar.  
* **Componentes do Android (Jetpack):**  
  * **ViewModel e LiveData:** Pra UI reagir às mudanças nos dados sem travar.  
  * **ViewBinding:** Para interagir com os layouts XML de forma segura.  
* **Tudo rodando liso com:** **Kotlin Coroutines**, para que as operações de rede não congelem o app.  
* **Motor de Vídeo:** A força bruta da **LibVLC for Android**. É por isso que ele roda de tudo\!  
* **Imagens:** A biblioteca **Glide** pra carregar os logos dos canais rapidinho.  
* **UI:** Componentes do **Material Design** e já preparado para o visual **Edge-to-Edge**.  
* **Dependências:** Tudo organizado com o **Gradle Version Catalog** (libs.versions.toml).

## **🚀 Como faço pra rodar?**

É moleza\!

1. Clone o repositório:  
   git clone https://github.com/admjorgeluiz/TVPlayer.git

2. Abra o projeto no Android Studio.  
3. Espere o Gradle fazer sua mágica e baixar tudo.  
4. Dê o play num emulador ou no seu celular.  
5. Na tela principal, é só clicar no botão de mais (+) e adicionar sua lista\!

## **🔮 E o que vem por aí? (Roadmap)**

* \[x\] Implementar o Picture-in-Picture (PiP). Já tá feito\!  
* \[ \] Adicionar uma seção de "Favoritos" pra guardar aqueles canais que você mais gosta.  
* \[ \] Fazer a lista funcionar offline, guardando-a num banco de dados (Room).  
* \[ \] Dar um "up" no player com mais controles, como volume e seleção de áudio/legenda.

Feito por Jorge Nascimento.
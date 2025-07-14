# **TVPlayer \- Player de Listas M3U para Android**

## **📖 Sobre o Projeto**

**TVPlayer** é um aplicativo Android nativo, desenvolvido em Kotlin, projetado para oferecer uma experiência de usuário fluida e robusta para carregar e assistir a conteúdos de listas de reprodução no formato M3U. O app permite que os usuários adicionem listas a partir de uma URL ou de um arquivo local, organizando os canais por categorias e oferecendo um player de vídeo avançado e intuitivo.

Este projeto foi construído seguindo as mais modernas práticas de desenvolvimento Android, com foco em uma arquitetura limpa, performance e manutenibilidade.

## **✨ Funcionalidades Principais**

* **Carregamento Flexível de Listas:** Adicione sua lista de canais M3U a partir de uma URL da web ou selecionando um arquivo .m3u diretamente do seu dispositivo.
* **Parser Inteligente:** Um parser M3U robusto que extrai nome, logo, grupo e URL de cada item, com suporte para diferentes formatos de atributos.
* **Organização por Categorias:** Os canais são automaticamente agrupados em abas (Todos, Canais, Filmes, Séries, Outros) para uma navegação mais fácil e organizada.
* **Busca em Tempo Real:** Filtre rapidamente a lista de canais digitando o nome do canal.
* **Player de Vídeo Avançado (baseado em LibVLC):**
    * Alta compatibilidade com diversos formatos de vídeo e codecs.
    * **Controles por Gestos:** Ajuste o **brilho** (deslizando na esquerda da tela) e o **volume** (deslizando na direita) de forma intuitiva.
    * Interface limpa com controles que desaparecem automaticamente.
    * Suporte para modo tela cheia e rotação de tela.
* **Persistência de Dados:** O aplicativo salva a última lista carregada para que você não precise inseri-la novamente a cada uso.

## **🛠️ Tecnologias e Arquitetura**

Este projeto foi desenvolvido utilizando as seguintes tecnologias e padrões arquitetônicos:

* **Linguagem:** 100% [Kotlin](https://kotlinlang.org/)
* **Arquitetura:** **MVVM (Model-View-ViewModel)**, garantindo um código desacoplado, testável e resiliente a mudanças de configuração.
* **Componentes de Arquitetura Android (Jetpack):**
    * **ViewModel:** Para gerenciar os dados da UI de forma consciente do ciclo de vida.
    * **LiveData:** Para notificar a UI sobre mudanças nos dados de forma reativa.
    * **ViewBinding:** Para uma interação segura e eficiente com as views do XML.
* **Assincronismo:** **Kotlin Coroutines** para executar operações de rede e I/O em segundo plano, mantendo a UI sempre responsiva.
* **Player de Vídeo:** **LibVLC for Android**, uma biblioteca poderosa e de alta performance para reprodução de mídia.
* **Carregamento de Imagens:** **Glide** para carregar e cachear os logos dos canais de forma eficiente.
* **UI:** Componentes do **Material Design** para uma interface moderna e consistente.
* **Gerenciamento de Dependências:** **Gradle Version Catalog** (libs.versions.toml) para uma gestão centralizada e limpa das bibliotecas.

## **🚀 Como Usar**

1. Clone o repositório:  
   git clone https://github.com/admjorgeluiz/TVPlayer.git

2. Abra o projeto no Android Studio.
3. Aguarde o Gradle sincronizar as dependências.
4. Execute o aplicativo em um emulador ou dispositivo físico.
5. Na tela principal, clique no botão flutuante (+) para adicionar uma nova lista via URL ou arquivo local.

## **🔮 Próximos Passos (Roadmap)**

* \[ \] Implementar funcionalidade de **Picture-in-Picture (PiP)** no player de vídeo.
* \[ \] Adicionar uma seção de "Favoritos".
* \[ \] Implementar cache da lista de canais em um banco de dados local (Room) para carregamento offline.
* \[ \] Melhorar a interface do player com mais controles (ex: seleção de faixa de áudio/legenda).

Desenvolvido por Jorge Nascimento.
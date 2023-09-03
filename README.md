# Sistema Distribuído de Armazenamento de Chave-Valor (Key-Value Store)

O Sistema Distribuído de Armazenamento de Chave-Valor é composto por 3 servidores com IPs e portas conhecidas, além de vários clientes. Os clientes podem realizar requisições em qualquer servidor, seja para inserir informações (PUT) ou para obter informações (GET).

## Funcionamento

1. **Servidores**: O sistema possui 3 servidores que armazenam pares chave-valor de forma replicada e consistente. Inicialmente, um dos servidores é escolhido como líder, sendo o único que pode realizar requisições PUT. No entanto, todos os servidores podem responder requisições GET.

2. **Clientes**: Os clientes podem enviar requisições para qualquer servidor disponível. Eles têm a capacidade de inserir pares chave-valor no sistema através de requisições PUT e obter valores associados a uma chave através de requisições GET.

3. **Replicação e Consistência**: O sistema mantém os dados replicados entre os servidores para garantir a consistência dos pares chave-valor. Dessa forma, os clientes podem realizar requisições em qualquer servidor sem se preocupar com a perda de dados.

## Implementação

O sistema foi implementado em Java, utilizando TCP como protocolo da camada de transporte para a comunicação entre os servidores e os clientes. A replicação dos dados entre os servidores é realizada de forma síncrona para garantir a consistência dos dados em todos os servidores..

## Executando o Sistema

Para executar o sistema, siga as seguintes etapas:

1. **Compilação**: Abra o IntelliJ IDEA ou outro IDE de sua preferência e importe os arquivos do projeto. Certifique-se de que todos os arquivos .java estejam presentes no projeto. Em seguida, compile o projeto para gerar os arquivos .class.

2. **Execução dos Servidores**: Inicie a execução de três instâncias da classe `Servidor.java`, uma para cada servidor. Cada servidor deve ser configurado com um IP e porta conhecidos.

3. **Execução dos Clientes**: Inicie a execução de várias instâncias da classe `Cliente.java`, representando os clientes do sistema. Os clientes podem fazer requisições PUT e GET para os servidores, inserindo chaves e valores ou buscando valores associados a chaves.

4. **Observações**: O sistema foi desenvolvido de forma simplificada e não aborda todos os aspectos de um sistema distribuído completo. Para uma utilização em produção, seriam necessários ajustes e considerações adicionais.

## Licença

Este projeto está licenciado sob a [licença do MIT](https://opensource.org/licenses/MIT). Consulte o arquivo LICENSE para obter mais informações sobre a licença.

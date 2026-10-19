# Bot Lanchonete 🍔

Atendente de WhatsApp para lanchonetes. O bot mostra o cardápio, anota o pedido completo (itens,
nome, entrega ou retirada, endereço e pagamento), confirma com o cliente e registra o pedido com o
total calculado pelo sistema.

Tem dois modos:

| Modo | Como o cliente conversa | Custo |
|------|-------------------------|-------|
| **Menu** | Digita números e códigos do cardápio (`2`, `X3 2 sem tomate`, `0`...) | Grátis |
| **IA** | Escreve do jeito que quiser ("quero 2 x-bacon sem tomate pra entregar") | Alguns centavos por pedido (API do Claude) |

No modo `auto` (padrão), o bot usa a IA quando a variável `ANTHROPIC_API_KEY` existe e o menu quando
não existe. Para forçar um modo: `BOT_MODO=menu` ou `BOT_MODO=ia`.

Feito com **Java 21 + Spring Boot**, **PostgreSQL** para os pedidos, **Claude (Anthropic)** para a
conversa e a **WhatsApp Cloud API** (oficial da Meta) para as mensagens.

## Como funciona

```
Cliente no WhatsApp ──> Meta ──> POST /webhook ──> AtendenteIA (Claude) ──> resposta pelo WhatsApp
                                                        │
                                                        └─ ferramenta registrar_pedido ──> PedidoService
```

- **AtendenteMenu**: o modo grátis. Guarda em que etapa do pedido cada cliente está (carrinho, nome,
  entrega, endereço, pagamento, troco, confirmação).
- **AtendenteIA**: guarda a conversa de cada cliente e manda para o Claude junto com o cardápio.
  Quando o cliente confirma, o Claude chama a ferramenta `registrar_pedido`.
- **PedidoService**: confere se os itens existem, calcula os preços e o total pelo cardápio e salva
  no PostgreSQL (tabelas `pedidos` e `pedido_itens`, criadas sozinhas pelo JPA). A IA nunca define o
  valor cobrado.
- **cardapio.json**: o cardápio, o horário, a taxa de entrega e as formas de pagamento.
  Para atender outra lanchonete, basta trocar esse arquivo.
- **Simulador** (`http://localhost:8080`): um chat parecido com o WhatsApp e a lista de pedidos,
  para testar e demonstrar sem configurar o WhatsApp.

## Rodando no computador

Precisa de Java 21+, Maven e PostgreSQL. A chave da API do Claude (https://console.anthropic.com)
só é necessária para o modo IA.

1. Crie o banco: `CREATE DATABASE bot_lanchonete;`
2. Rode (Windows, PowerShell):

```bash
$env:DB_PASSWORD="senha-do-postgres"
# opcional, para o modo IA:
$env:ANTHROPIC_API_KEY="sua-chave"
mvn spring-boot:run
```

Usuário e endereço do banco podem ser trocados com `DB_USER` e `DB_URL`.

Abra http://localhost:8080 e mande um "oi".

Testes: `mvn test`

## Ligando no WhatsApp de verdade

1. Crie um app em https://developers.facebook.com, do tipo **Empresa**, e adicione o produto **WhatsApp**.
   A Meta dá um número de teste grátis para começar.
2. Em *WhatsApp > Configuração da API*, copie o **token** e o **ID do número de telefone**.
3. Deixe o servidor acessível na internet com HTTPS (para testar: `ngrok http 8080`).
4. Rode com as variáveis:

   ```bash
   $env:ANTHROPIC_API_KEY="sua-chave"
   $env:WHATSAPP_TOKEN="token-da-meta"
   $env:WHATSAPP_PHONE_NUMBER_ID="id-do-numero"
   $env:WHATSAPP_VERIFY_TOKEN="uma-senha-que-voce-inventa"
   $env:WHATSAPP_APP_SECRET="chave-secreta-do-app"   # Configurações do app > Básico
   mvn spring-boot:run
   ```

5. Em *WhatsApp > Configuração*, cadastre o webhook `https://SEU-ENDERECO/webhook` com o mesmo
   `WHATSAPP_VERIFY_TOKEN` e assine o campo **messages**.
6. Mande uma mensagem para o número de teste.

## Endpoints

| Método | Caminho        | Para quê                                           |
|--------|----------------|----------------------------------------------------|
| GET    | `/webhook`     | Verificação do webhook pela Meta                   |
| POST   | `/webhook`     | Mensagens recebidas do WhatsApp                    |
| POST   | `/api/simular` | Testar sem WhatsApp: `{"telefone": "...", "mensagem": "..."}` |
| GET    | `/api/pedidos` | Lista dos pedidos registrados                      |
| GET    | `/api/modo`    | Modo em uso: `menu` ou `ia`                        |

## Detalhes

- Modelo: `claude-opus-5-5` (troque em `ia.modelo` no `application.properties`), com esforço baixo
  para responder rápido e cache de prompt para baratear as conversas.
- Se o modelo recusar uma mensagem, a API tenta automaticamente com outro modelo (fallback).
- O webhook confere a assinatura `X-Hub-Signature-256` quando `WHATSAPP_APP_SECRET` está configurado
  e ignora mensagens repetidas.
- Uma conversa sem mensagens por 2 horas é encerrada; o próximo "oi" começa do zero.

## Próximos passos

- Salvar também as conversas no banco (hoje ficam na memória e recomeçam se o bot reiniciar).
- Painel para a cozinha mudar o status do pedido e avisar o cliente pelo WhatsApp.
- Entender áudio e foto.

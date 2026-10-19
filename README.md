# Bot Lanchonete 🍔

Atendente de IA no WhatsApp para lanchonetes. O bot tira dúvidas sobre o cardápio, anota o pedido
completo (itens, nome, entrega ou retirada, endereço e pagamento), confirma com o cliente e registra
o pedido com o total calculado pelo sistema.

Feito com **Java 21 + Spring Boot**, **Claude (Anthropic)** para a conversa e a
**WhatsApp Cloud API** (oficial da Meta) para as mensagens.

## Como funciona

```
Cliente no WhatsApp ──> Meta ──> POST /webhook ──> AtendenteIA (Claude) ──> resposta pelo WhatsApp
                                                        │
                                                        └─ ferramenta registrar_pedido ──> PedidoService
```

- **AtendenteIA**: guarda a conversa de cada cliente e manda para o Claude junto com o cardápio.
  Quando o cliente confirma, o Claude chama a ferramenta `registrar_pedido`.
- **PedidoService**: confere se os itens existem e calcula os preços e o total pelo cardápio.
  A IA nunca define o valor cobrado.
- **cardapio.json**: o cardápio, o horário, a taxa de entrega e as formas de pagamento.
  Para atender outra lanchonete, basta trocar esse arquivo.
- **Simulador** (`http://localhost:8080`): um chat parecido com o WhatsApp e a lista de pedidos,
  para testar e demonstrar sem configurar o WhatsApp.

## Rodando no computador

Precisa de Java 21+ e Maven, e de uma chave da API do Claude (crie em https://console.anthropic.com).

```bash
# Windows (PowerShell)
$env:ANTHROPIC_API_KEY="sua-chave"
mvn spring-boot:run
```

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

## Detalhes

- Modelo: `claude-opus-5-5` (troque em `ia.modelo` no `application.properties`), com esforço baixo
  para responder rápido e cache de prompt para baratear as conversas.
- Se o modelo recusar uma mensagem, a API tenta automaticamente com outro modelo (fallback).
- O webhook confere a assinatura `X-Hub-Signature-256` quando `WHATSAPP_APP_SECRET` está configurado
  e ignora mensagens repetidas.
- Uma conversa sem mensagens por 2 horas é encerrada; o próximo "oi" começa do zero.

## Próximos passos

- Salvar pedidos e conversas no PostgreSQL (hoje ficam na memória e somem ao reiniciar).
- Painel para a cozinha mudar o status do pedido e avisar o cliente pelo WhatsApp.
- Entender áudio e foto.

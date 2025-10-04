# java-sample-app-arm-v2

Este repositório contém um exemplo mínimo de aplicação Java preparada para ser empacotada em container e implantada em um cluster Kubernetes (por exemplo OKE na Oracle Cloud Infrastructure - OCI). A pasta `deploy/` já inclui manifests de deployment que o ArgoCD pode usar para sincronizar a aplicação.

Este README descreve, em português, os passos para:

- Preparar a infraestrutura na OCI (visão geral)
- Configurar um pipeline para build e deploy (ex.: OCI DevOps + alternativa GitHub Actions)
- Configurar segredos (OCI Vault, secrets no Kubernetes e credenciais do ArgoCD)
- Instalar e configurar o ArgoCD para sincronizar a pasta `deploy/`

Nota: os passos abaixo são práticos e genéricos — adapte OCIDs, nomes e regiões à sua tenancy/conta OCI.

## Pré-requisitos

- Conta OCI com permissões para criar VCN, OKE, Container Registry (OCIR), Vault e DevOps resources
- OCI CLI instalado e configurado (profile) — https://docs.oracle.com/iaas/Content/SDKDocs/cli.htm
- kubectl instalado
- docker (ou podman) para build/push de imagens
- Helm (opcional, usado para instalar ArgoCD)
- argocd CLI (opcional, para criar apps pelo CLI)

## 1) Criar a infraestrutura (visão geral)

Recomendo usar Terraform ou os guias oficiais da OCI para criar:

- VCN com subnets públicas/privadas
- Cluster OKE (Oracle Kubernetes Engine)
- OCIR (Oracle Cloud Infrastructure Registry) para armazenar as imagens
- Vault (se desejar gerenciar segredos centralmente)

Em alto nível:

1. Crie um VCN e subnets.
2. Crie um cluster OKE em uma das subnets privadas (com worker nodes ou node pools compatíveis ARM/x86 conforme necessário).
3. Habilite acesso ao cluster (gere o kubeconfig usando OCI CLI ou Console).
4. Crie um repositório no OCIR (ou use o namespace padrão da sua tenancy).

Links úteis:

- OKE Quickstart: https://docs.oracle.com/en-us/iaas/Content/ContEng/Concepts/contengoverview.htm
- OCIR: https://docs.oracle.com/en-us/iaas/Content/Registry/Concepts/registryoverview.htm

## 2) Build da imagem e push para OCIR

1. Faça login no OCIR (substitua `region` pelo seu código de região, ex.: `iad`):

```powershell
# Gere um Auth Token no Console OCI (User -> Auth Tokens) e use-o como senha
docker login <region>.ocir.io -u '<tenancy-namespace>/<username>' -p '<auth-token>'
```

2. Build e tag da imagem (exemplo):

```powershell
docker build -t myapp:latest .
docker tag myapp:latest <region>.ocir.io/<tenancy-namespace>/java-sample-app-arm-v2:latest
docker push <region>.ocir.io/<tenancy-namespace>/java-sample-app-arm-v2:latest
```

Substitua `<tenancy-namespace>`, `<username>` e `<region>` pelos valores da sua tenancy.

## 3) Configuração do pipeline

Opção A — OCI DevOps (recomendado se usar OCI end-to-end):

1. No Console OCI, vá em Developer Services -> DevOps -> Toolchains / Pipelines.
2. Crie um Connection/Repository apontando para este repositório Git (ou use o repositório do Code Repository da OCI).
3. Crie um Build Pipeline com etapas:
	 - Build: executar `docker build` e `docker push` para OCIR (use credenciais via OCI Vault)
	 - (Opcional) Testes: executar unit/integration tests
4. Crie um Deploy stage (ou use ArgoCD para deploy contínuo):
	 - Se for empurrar direto ao cluster, use um job que aplique `kubectl apply -f deploy/` com kubeconfig armazenado como secret.
	 - Melhor prática: deixar o pipeline apenas construir e publicar a imagem; deixar o ArgoCD sincronizar as alterações do manifest.

Variáveis e segredos que você precisará no pipeline:

- OCIR auth token (ou usar credenciais do usuário)
- OCID do compartment (quando aplicável)
- OCID do secret do Vault (se usar Vault)
- kubeconfig ou credenciais para aplicar manifests (se não usar ArgoCD)

Opção B — GitHub Actions (alternativa):

Um exemplo mínimo do job:

```yaml
# .github/workflows/ci.yml (exemplo)
name: CI
on: [push]
jobs:
	build:
		runs-on: ubuntu-latest
		steps:
			- uses: actions/checkout@v4
			- name: Build image
				run: |
					docker build -t myapp:latest .
					docker tag myapp:latest <region>.ocir.io/<tenancy-namespace>/java-sample-app-arm-v2:latest
			- name: Login to OCIR
				run: echo ${{ secrets.OCIR_TOKEN }} | docker login <region>.ocir.io -u '${{ secrets.OCIR_USER }}' --password-stdin
			- name: Push image
				run: docker push <region>.ocir.io/<tenancy-namespace>/java-sample-app-arm-v2:latest
```

Armazene `OCIR_TOKEN` e `OCIR_USER` como secrets no GitHub.

## 4) Configuração de segredos

Você tem basicamente três lugares para guardar segredos:

- OCI Vault (recomendado para produção na OCI)
- Git provider secrets (GitHub Secrets, OCI DevOps variable store)
- Kubernetes Secrets (para uso dentro do cluster e referência pelo ArgoCD)

Criar um secret no OCI Vault (exemplo):

1. No Console OCI, vá em Security -> Vault -> Secrets -> Criar Secret.
2. Armazene o token do OCIR e/ou kubeconfig como secret.
3. No OCI DevOps, ao criar um Resource/Job, referencie o Secret OCID para buscar a credencial.

Criar um Secret no Kubernetes (exemplo para kube credentials ou credenciais internas):

```powershell
kubectl create secret generic ocir-credentials --from-literal=username='<tenancy-namespace>/<username>' --from-literal=password='<auth-token>' -n default
kubectl create secret generic myapp-config --from-file=./config.yaml -n default
```

ArgoCD também pode usar secrets para armazenar credenciais de repositório e token de acesso. A forma recomendada é criar um `Secret` do tipo `argocd-secret` ou usar a UI/CLI do ArgoCD para adicionar repositórios e credenciais.

## 5) Instalar e configurar o ArgoCD

1. Instalar ArgoCD no cluster (namespace `argocd`):

```powershell
kubectl create namespace argocd
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml
```

2. Expor o serviço `argocd-server` (para acesso local/temporário):

```powershell
# Port-forward (modo rápido para testes locais)
kubectl -n argocd port-forward svc/argocd-server 8080:443
# Agora acesse https://localhost:8080
```

3. Obter senha inicial do `admin` (é o nome do pod argocd-server ou o secret):

```powershell
kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath="{.data.password}" | base64 --decode
```

4. Fazer login com `argocd` CLI (exemplo local):

```powershell
argocd login localhost:8080 --username admin --password '<senha-obtida>' --insecure
```

5. Criar uma Application do ArgoCD apontando para este repositório e para a pasta `deploy/`:

```powershell
argocd app create java-sample-app --repo 'https://seu-repo-git.git' --path deploy --dest-server 'https://kubernetes.default.svc' --dest-namespace default
argocd app sync java-sample-app
```

Substitua `https://seu-repo-git.git` pelo URL deste repositório (público ou privado). Se o repositório for privado, adicione as credenciais do repositório nas settings do ArgoCD (em Settings -> Repositories, via UI ou `argocd repo add ...`).

6. Estratégia recomendada de deploy:

- Pipeline: constrói e publica imagens no OCIR
- Manifests: referência a tags/imagens atualizadas (pode usar kustomize, k8s manifests ou Helm)
- ArgoCD: monitora `deploy/` e faz o sync para o cluster quando detecta alterações

Dica: para evitar tag imutável `:latest`, utilize tags baseadas em `git commit SHA` ou `build number` e atualize o manifest antes do commit (ou use Image Updater/Flux/ArgoCD Image Updater para automatizar).

## 6) Verificação rápida

1. Verifique se a imagem está no OCIR:

```powershell
# Acesse via Console OCIR ou use a API/CLI para listar
```

2. Verifique se os pods do cluster estão prontos:

```powershell
kubectl get nodes
kubectl get pods -n default
kubectl get deploy -n default
```

3. Verifique o status do ArgoCD:

```powershell
argocd app get java-sample-app
```

## 7) Boas práticas e próximos passos

- Automatize o provisionamento de infraestrutura com Terraform
- Não armazene tokens em texto puro; use OCI Vault e referência de secrets
- Use tags imutáveis para imagens em produção
- Configure monitoramento (Prometheus/Grafana) e alertas

---

Se quiser, eu posso:

- Gerar um exemplo de pipeline OCI DevOps específico para este repositório
- Gerar um workflow de GitHub Actions completo que faça build/push e abra um Pull Request atualizando o manifest com a nova tag
- Criar os manifests do ArgoCD (Application manifest) prontos para aplicar

Diga qual desses você prefere que eu crie agora.

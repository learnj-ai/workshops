# How to Preview the Tutorial with HonKit

## Quick Start

### Option 1: Using npx (No Installation Required)

From the `tutorials` directory, run:

```bash
cd tutorials
npx honkit serve
```

Then open your browser to: `http://localhost:4000`

### Option 2: Install HonKit Globally

```bash
npm install -g honkit
cd tutorials
honkit serve
```

### Option 3: Using Local Installation

If you prefer a local installation, first install the dependencies:

```bash
cd tutorials
npm install
npm start
```

## Building Static HTML

To generate static HTML files:

```bash
cd tutorials
honkit build
```

The output will be in the `_book` directory.

## Customization

The `book.json` file contains all configuration options:

- **Plugins**: Add or remove HonKit plugins
- **Theme**: Customize the appearance
- **Variables**: Set workshop-specific variables
- **Title & Description**: Modify tutorial metadata

## Available Commands

- `honkit serve` - Start development server with live reload
- `honkit build` - Generate static HTML
- `honkit pdf` - Generate PDF (requires calibre)
- `honkit epub` - Generate EPUB

## File Structure

```
tutorials/
├── book.json           # HonKit configuration
├── README.md          # Tutorial introduction
├── SUMMARY.md         # Table of contents (all modules)
├── package.json       # npm dependencies
├── module-01-vectors-embeddings/
├── module-02-advanced-rag/
├── module-03-tools-mcp/
├── module-04-chatbots-to-agents/
├── module-05-security-guardrails/
└── module-06-enterprise-production/
```

## Tips

- The live server automatically reloads when you edit markdown files
- Use `Ctrl+C` to stop the server
- Check `http://localhost:4000` for the rendered tutorial
- All modules are now combined into one comprehensive tutorial

## Troubleshooting

If you encounter issues:

1. Clear the HonKit cache: `rm -rf _book`
2. Reinstall dependencies: `npm install`
3. Check Node.js version: `node --version` (requires Node.js 12+)

import express from 'express';
import cors from 'cors';
import morgan from 'morgan';

const app = express();
const PORT = process.env.PORT || 8080;

// Core Java Microservices URLs
const SPRING_BOOT_SERVICES = {
    GRIEVANCE: process.env.GRIEVANCE_SERVICE_URL || 'http://localhost:8081/api/grievances',
    COLLATERAL: process.env.COLLATERAL_SERVICE_URL || 'http://localhost:8082/api/collateral'
};

// Global Middleware Layers
app.use(cors({
    origin: ['http://localhost:5173', 'http://localhost:3000'],
    methods: ['GET', 'POST', 'PUT', 'DELETE'],
    allowedHeaders: ['Content-Type', 'Authorization']
}));
app.use(express.json());
app.use(morgan('dev'));

// Proxy Route: Fetch All Tickets
app.get('/api/grievances/all', async (req, res) => {
    try {
        const response = await fetch(`${SPRING_BOOT_SERVICES.GRIEVANCE}/all`);
        if (!response.ok) {
            return res.status(response.status).json({ message: "Error fetching data from core database service." });
        }
        const data = await response.json();
        res.status(200).json(data);
    } catch (error) {
        console.error("Proxy Connection Error (GET /all):", error.message);
        res.status(500).json({ error: "Grievance Service downstream failure." });
    }
});

// Proxy Route: Forward New Grievance Ticket Payload
app.post('/api/grievances/create', async (req, res) => {
    try {
        const response = await fetch(`${SPRING_BOOT_SERVICES.GRIEVANCE}/create`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(req.body)
        });
        const data = await response.json();
        if (!response.ok) return res.status(response.status).json(data);
        res.status(201).json(data);
    } catch (error) {
        console.error("Proxy Connection Error (POST /create):", error.message);
        res.status(500).json({ error: "Internal Gateway Routing Failure." });
    }
});

app.get('/health', (req, res) => {
    res.status(200).json({ status: "UP", gateway: "ClearClose Node Proxy (ESM)" });
});

app.listen(PORT, () => {
    console.log(`====================================================`);
    console.log(`🚀 ClearClose API Gateway running on port: ${PORT}`);
    console.log(`🔗 Proxying Grievances to: ${SPRING_BOOT_SERVICES.GRIEVANCE}`);
    console.log(`====================================================`);
});
